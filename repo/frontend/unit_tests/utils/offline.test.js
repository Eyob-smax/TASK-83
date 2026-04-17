import { describe, it, expect, vi, beforeEach } from 'vitest'

// Use vi.hoisted so the store maps exist before vi.mock runs
const { stores } = vi.hoisted(() => ({
  stores: { offline_queue: new Map(), roster_cache: new Map() }
}))

vi.mock('localforage', () => ({
  default: {
    createInstance: vi.fn(({ storeName }) => {
      const map = stores[storeName]
      return {
        _config: { name: 'eventops', storeName },
        getItem: vi.fn(async (k) => map.get(k) ?? null),
        setItem: vi.fn(async (k, v) => { map.set(k, v); return v }),
        removeItem: vi.fn(async (k) => { map.delete(k) }),
        keys: vi.fn(async () => [...map.keys()]),
        clear: vi.fn(async () => { map.clear() })
      }
    })
  }
}))

import {
  offlineQueue,
  rosterCache,
  enqueuePendingAction,
  getPendingActions,
  removePendingAction,
  clearPendingActions,
  cacheRosterData,
  getCachedRoster
} from '../../src/utils/offline.js'

describe('offline utils', () => {
  beforeEach(() => {
    stores.offline_queue.clear()
    stores.roster_cache.clear()
  })

  it('offlineQueue localforage instance exists', () => {
    expect(offlineQueue).toBeDefined()
    expect(offlineQueue._config.storeName).toBe('offline_queue')
  })

  it('rosterCache localforage instance exists', () => {
    expect(rosterCache).toBeDefined()
    expect(rosterCache._config.storeName).toBe('roster_cache')
  })

  it('enqueuePendingAction adds a timestamped entry to the queue', async () => {
    const entry = await enqueuePendingAction({ method: 'POST', url: '/x' })
    expect(entry.id).toBeTruthy()
    expect(entry.queuedAt).toBeTruthy()
    const all = await getPendingActions()
    expect(all).toHaveLength(1)
  })

  it('getPendingActions returns empty array when queue is empty', async () => {
    const all = await getPendingActions()
    expect(all).toEqual([])
  })

  it('getPendingActions returns multiple actions', async () => {
    await enqueuePendingAction({ method: 'POST', url: '/a' })
    await enqueuePendingAction({ method: 'POST', url: '/b' })
    const all = await getPendingActions()
    expect(all).toHaveLength(2)
  })

  it('removePendingAction removes a single entry', async () => {
    const entry = await enqueuePendingAction({ method: 'POST', url: '/x' })
    await removePendingAction(entry.id)
    const all = await getPendingActions()
    expect(all).toHaveLength(0)
  })

  it('clearPendingActions clears the entire queue', async () => {
    await enqueuePendingAction({ method: 'POST', url: '/a' })
    await enqueuePendingAction({ method: 'POST', url: '/b' })
    await clearPendingActions()
    const all = await getPendingActions()
    expect(all).toHaveLength(0)
  })

  it('cacheRosterData + getCachedRoster roundtrip', async () => {
    await cacheRosterData('s1', [{ id: 'u1' }])
    const data = await getCachedRoster('s1')
    expect(data).toEqual([{ id: 'u1' }])
  })

  it('getCachedRoster returns null for missing sessions', async () => {
    const data = await getCachedRoster('nonexistent')
    expect(data).toBeNull()
  })

  it('getCachedRoster returns null and removes expired entries', async () => {
    stores.roster_cache.set('s-exp', { data: ['x'], cachedAt: 0, expiresAt: 1 })
    const data = await getCachedRoster('s-exp')
    expect(data).toBeNull()
    expect(stores.roster_cache.has('s-exp')).toBe(false)
  })
})
