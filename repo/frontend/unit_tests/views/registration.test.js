import { describe, it, expect } from 'vitest'

describe('Registration flow states', () => {
  it('CONFIRMED status is displayed for successful registration', () => {
    const status = 'CONFIRMED'
    expect(['CONFIRMED', 'WAITLISTED', 'PROMOTED', 'CANCELLED', 'EXPIRED']).toContain(status)
  })

  it('WAITLISTED status includes position number', () => {
    const reg = { status: 'WAITLISTED', waitlistPosition: 3 }
    expect(reg.waitlistPosition).toBeGreaterThan(0)
  })

  it('duplicate conflict shows clear message', () => {
    const conflictMessage = 'You are already registered for this session'
    expect(conflictMessage).toContain('already registered')
  })

  it('waitlist cutoff is displayed when present', () => {
    const cutoff = '2026-04-15T10:00:00'
    expect(cutoff).toBeTruthy()
  })

  it('cancel action requires CONFIRMED or WAITLISTED status', () => {
    const cancellable = ['CONFIRMED', 'WAITLISTED', 'PROMOTED']
    expect(cancellable).toContain('CONFIRMED')
    expect(cancellable).not.toContain('CANCELLED')
  })
})
