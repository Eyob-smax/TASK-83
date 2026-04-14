/**
 * Offline detection and pending-action queue management.
 * Uses localforage (IndexedDB-backed) for persistent storage.
 */
import localforage from 'localforage'

export const offlineQueue = localforage.createInstance({
  name: 'eventops',
  storeName: 'offline_queue'
})

export const rosterCache = localforage.createInstance({
  name: 'eventops',
  storeName: 'roster_cache'
})

/**
 * Enqueue a pending action for later replay.
 */
export async function enqueuePendingAction(action) {
  const id = Date.now() + '_' + Math.random().toString(36).substr(2, 9)
  const entry = { id, ...action, queuedAt: new Date().toISOString() }
  await offlineQueue.setItem(id, entry)
  return entry
}

/**
 * Get all pending actions from the queue.
 */
export async function getPendingActions() {
  const actions = []
  const keys = await offlineQueue.keys()
  for (const key of keys) {
    const action = await offlineQueue.getItem(key)
    if (action) actions.push(action)
  }
  return actions
}

/**
 * Remove a specific action from the queue.
 */
export async function removePendingAction(id) {
  await offlineQueue.removeItem(id)
}

/**
 * Clear all pending actions.
 */
export async function clearPendingActions() {
  await offlineQueue.clear()
}

/**
 * Cache roster data for offline access with TTL.
 */
export async function cacheRosterData(sessionId, data, ttlMs = 5 * 60 * 1000) {
  await rosterCache.setItem(sessionId, {
    data,
    cachedAt: Date.now(),
    expiresAt: Date.now() + ttlMs
  })
}

/**
 * Get cached roster data if not expired.
 */
export async function getCachedRoster(sessionId) {
  const entry = await rosterCache.getItem(sessionId)
  if (!entry) return null
  if (Date.now() > entry.expiresAt) {
    await rosterCache.removeItem(sessionId)
    return null
  }
  return entry.data
}
