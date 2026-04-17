/**
 * Unit tests for the cache utility module.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

// Use vi.hoisted so the store exists before vi.mock runs
const { store } = vi.hoisted(() => ({ store: new Map() }))

vi.mock('localforage', () => ({
  default: {
    createInstance: vi.fn(() => ({
      _config: { name: 'eventops', storeName: 'data_cache' },
      getItem: vi.fn(async (k) => store.get(k) ?? null),
      setItem: vi.fn(async (k, v) => { store.set(k, v); return v }),
      removeItem: vi.fn(async (k) => { store.delete(k) }),
      clear: vi.fn(async () => { store.clear() })
    }))
  }
}))

import { dataCache, cacheGet, cacheSet, cacheInvalidate, cacheInvalidateAll } from '../../src/utils/cache.js'

describe('cache utils', () => {
  beforeEach(() => { store.clear() })

  it('dataCache localforage instance exists', () => {
    expect(dataCache).toBeDefined()
  })

  it('dataCache has correct database name', () => {
    expect(dataCache._config.name).toBe('eventops')
  })

  it('dataCache has correct store name', () => {
    expect(dataCache._config.storeName).toBe('data_cache')
  })

  it('cacheSet then cacheGet returns the stored value', async () => {
    await cacheSet('k1', { foo: 'bar' })
    const v = await cacheGet('k1')
    expect(v).toEqual({ foo: 'bar' })
  })

  it('cacheGet returns null for missing keys', async () => {
    const v = await cacheGet('nope')
    expect(v).toBeNull()
  })

  it('cacheGet returns null and removes expired entries', async () => {
    // Seed with a long-expired entry directly
    store.set('k2', { value: 'old', cachedAt: 0, expiresAt: 1 })
    const v = await cacheGet('k2')
    expect(v).toBeNull()
    expect(store.has('k2')).toBe(false)
  })

  it('cacheInvalidate removes a single key', async () => {
    await cacheSet('k3', 'v3')
    await cacheInvalidate('k3')
    expect(await cacheGet('k3')).toBeNull()
  })

  it('cacheInvalidateAll clears the entire cache', async () => {
    await cacheSet('a', 1)
    await cacheSet('b', 2)
    await cacheInvalidateAll()
    expect(await cacheGet('a')).toBeNull()
    expect(await cacheGet('b')).toBeNull()
  })

  it('cacheSet uses custom TTL when provided', async () => {
    await cacheSet('k4', 'v4', 100000)
    const raw = store.get('k4')
    expect(raw.expiresAt - raw.cachedAt).toBe(100000)
  })
})
