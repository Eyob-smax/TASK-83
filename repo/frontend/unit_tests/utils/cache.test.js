/**
 * Unit tests for the cache utility module.
 *
 * Verifies that the dataCache localforage instance exists
 * and is configured with the correct database and store names.
 */
import { describe, it, expect } from 'vitest'
import { dataCache } from '../../src/utils/cache.js'

describe('cache utils', () => {
  it('dataCache localforage instance exists', () => {
    expect(dataCache).toBeDefined()
  })

  it('dataCache has correct database name', () => {
    expect(dataCache._config.name).toBe('eventops')
  })

  it('dataCache has correct store name', () => {
    expect(dataCache._config.storeName).toBe('data_cache')
  })
})
