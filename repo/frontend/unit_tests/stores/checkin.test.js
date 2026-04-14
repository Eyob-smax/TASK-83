import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useCheckinStore } from '../../src/stores/checkin.js'

vi.mock('../../src/api/checkin.js', () => ({
  getPasscode: vi.fn(),
  checkInAttendee: vi.fn(),
  getRoster: vi.fn(),
  getConflicts: vi.fn()
}))

import { getPasscode, checkInAttendee } from '../../src/api/checkin.js'

describe('checkin store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('fetchPasscode sets passcode and countdown', async () => {
    getPasscode.mockResolvedValue({
      data: { data: { passcode: '482917', remainingSeconds: 45 } }
    })
    const store = useCheckinStore()
    await store.fetchPasscode('session-1')
    expect(store.currentPasscode).toBe('482917')
    expect(store.passcodeCountdown).toBe(45)
  })

  it('performCheckIn returns result on success', async () => {
    checkInAttendee.mockResolvedValue({
      data: { data: { id: 'ci1', status: 'CHECKED_IN', message: 'Success' } }
    })
    const store = useCheckinStore()
    const result = await store.performCheckIn('s1', { userId: 'u1', passcode: '123456' })
    expect(result.status).toBe('CHECKED_IN')
  })

  it('performCheckIn sets conflictDetail on DUPLICATE_CHECKIN', async () => {
    checkInAttendee.mockRejectedValue({
      response: { data: { message: 'Already checked in', errors: [{ code: 'DUPLICATE_CHECKIN' }] } }
    })
    const store = useCheckinStore()
    await expect(store.performCheckIn('s1', {})).rejects.toThrow()
    expect(store.conflictDetail.code).toBe('DUPLICATE_CHECKIN')
  })

  it('performCheckIn sets conflictDetail on DEVICE_CONFLICT', async () => {
    checkInAttendee.mockRejectedValue({
      response: { data: { message: 'Device conflict', errors: [{ code: 'DEVICE_CONFLICT' }] } }
    })
    const store = useCheckinStore()
    await expect(store.performCheckIn('s1', {})).rejects.toThrow()
    expect(store.conflictDetail.code).toBe('DEVICE_CONFLICT')
  })

  it('performCheckIn sets error for non-conflict failures', async () => {
    checkInAttendee.mockRejectedValue({
      response: { data: { message: 'Window closed', errors: [{ code: 'WINDOW_CLOSED' }] } }
    })
    const store = useCheckinStore()
    await expect(store.performCheckIn('s1', {})).rejects.toThrow()
    expect(store.error).toBe('Window closed')
  })
})
