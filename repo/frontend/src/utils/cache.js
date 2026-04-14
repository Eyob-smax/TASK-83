/**
 * TTL-based cache utilities wrapping localforage.
 */
import localforage from 'localforage'

export const dataCache = localforage.createInstance({
  name: 'eventops',
  storeName: 'data_cache'
})

export async function cacheGet(key) {
  const entry = await dataCache.getItem(key)
  if (!entry) return null
  if (entry.expiresAt && Date.now() > entry.expiresAt) {
    await dataCache.removeItem(key)
    return null
  }
  return entry.value
}

export async function cacheSet(key, value, ttlMs = 5 * 60 * 1000) {
  await dataCache.setItem(key, {
    value,
    cachedAt: Date.now(),
    expiresAt: Date.now() + ttlMs
  })
}

export async function cacheInvalidate(key) {
  await dataCache.removeItem(key)
}

export async function cacheInvalidateAll() {
  await dataCache.clear()
}
