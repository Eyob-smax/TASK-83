import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../../src/api/client.js', () => ({
  default: {
    get: vi.fn(() => Promise.resolve({ data: {} })),
    post: vi.fn(() => Promise.resolve({ data: {} }))
  }
}))

import apiClient from '../../src/api/client.js'
import { getPasscode, checkInAttendee, getRoster, getConflicts } from '../../src/api/checkin.js'

describe('api/checkin', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getPasscode gets /checkin/sessions/{id}/passcode', async () => {
    await getPasscode('sess-1')
    expect(apiClient.get).toHaveBeenCalledWith('/checkin/sessions/sess-1/passcode')
  })

  it('checkInAttendee posts data to session endpoint', async () => {
    const data = { passcode: '123456', attendeeId: 'u-1' }
    await checkInAttendee('sess-1', data)
    expect(apiClient.post).toHaveBeenCalledWith('/checkin/sessions/sess-1', data)
  })

  it('getRoster gets /checkin/sessions/{id}/roster', async () => {
    await getRoster('sess-2')
    expect(apiClient.get).toHaveBeenCalledWith('/checkin/sessions/sess-2/roster')
  })

  it('getConflicts gets /checkin/sessions/{id}/conflicts', async () => {
    await getConflicts('sess-3')
    expect(apiClient.get).toHaveBeenCalledWith('/checkin/sessions/sess-3/conflicts')
  })
})
