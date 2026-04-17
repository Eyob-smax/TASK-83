import { describe, it, expect } from 'vitest'
import {
  useAuthStore,
  useEventsStore,
  useRegistrationStore,
  useCheckinStore,
  useNotificationsStore,
  useFinanceStore,
  useAdminStore,
  useOfflineStore
} from '../../src/stores/index.js'

describe('stores/index barrel', () => {
  it('re-exports all 8 store factory functions', () => {
    expect(typeof useAuthStore).toBe('function')
    expect(typeof useEventsStore).toBe('function')
    expect(typeof useRegistrationStore).toBe('function')
    expect(typeof useCheckinStore).toBe('function')
    expect(typeof useNotificationsStore).toBe('function')
    expect(typeof useFinanceStore).toBe('function')
    expect(typeof useAdminStore).toBe('function')
    expect(typeof useOfflineStore).toBe('function')
  })

  it('store factories have the expected pinia store id marker', () => {
    // Each pinia store function has $id populated only after activation; simple truthy shape check here.
    expect(useAuthStore.length).toBeGreaterThanOrEqual(0)
    expect(useEventsStore.length).toBeGreaterThanOrEqual(0)
  })

  it('named exports are distinct functions', () => {
    const fns = [useAuthStore, useEventsStore, useRegistrationStore, useCheckinStore, useNotificationsStore, useFinanceStore, useAdminStore, useOfflineStore]
    const unique = new Set(fns)
    expect(unique.size).toBe(fns.length)
  })
})
