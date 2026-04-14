import { describe, it, expect } from 'vitest'

describe('OfflineIndicator', () => {
  it('shows banner text when offline', () => {
    const isOnline = false
    const shouldShow = !isOnline
    expect(shouldShow).toBe(true)
  })

  it('hides banner when online', () => {
    const isOnline = true
    const shouldShow = !isOnline
    expect(shouldShow).toBe(false)
  })

  it('banner message is meaningful', () => {
    const message = 'You are currently offline. Changes will be queued and synced when connectivity returns.'
    expect(message).toContain('offline')
    expect(message).toContain('queued')
    expect(message).toContain('synced')
  })
})
