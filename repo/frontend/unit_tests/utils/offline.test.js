/**
 * Unit tests for the offline utility module.
 *
 * Verifies that localforage instances are created with the
 * correct database name and store names.
 */
import { describe, it, expect } from 'vitest'
import { offlineQueue, rosterCache } from '../../src/utils/offline.js'

describe('offline utils', () => {
  it('offlineQueue localforage instance exists', () => {
    expect(offlineQueue).toBeDefined()
  })

  it('offlineQueue has correct database name', () => {
    expect(offlineQueue._config.name).toBe('eventops')
  })

  it('offlineQueue has correct store name', () => {
    expect(offlineQueue._config.storeName).toBe('offline_queue')
  })

  it('rosterCache localforage instance exists', () => {
    expect(rosterCache).toBeDefined()
  })

  it('rosterCache has correct database name', () => {
    expect(rosterCache._config.name).toBe('eventops')
  })

  it('rosterCache has correct store name', () => {
    expect(rosterCache._config.storeName).toBe('roster_cache')
  })
})
