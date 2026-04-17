import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../../src/api/client.js', () => ({
  default: {
    get: vi.fn(() => Promise.resolve({ data: {} })),
    post: vi.fn(() => Promise.resolve({ data: {} })),
    put: vi.fn(() => Promise.resolve({ data: {} })),
    patch: vi.fn(() => Promise.resolve({ data: {} })),
    delete: vi.fn(() => Promise.resolve({ data: {} }))
  }
}))

import apiClient from '../../src/api/client.js'
import {
  register,
  listRegistrations,
  getRegistration,
  cancelRegistration,
  getWaitlistPositions
} from '../../src/api/registration.js'

describe('api/registration', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('register posts {sessionId} to /registrations', async () => {
    await register('sess-1')
    expect(apiClient.post).toHaveBeenCalledWith('/registrations', { sessionId: 'sess-1' })
  })

  it('listRegistrations forwards params', async () => {
    await listRegistrations({ status: 'CONFIRMED' })
    expect(apiClient.get).toHaveBeenCalledWith('/registrations', { params: { status: 'CONFIRMED' } })
  })

  it('getRegistration gets by id', async () => {
    await getRegistration('reg-1')
    expect(apiClient.get).toHaveBeenCalledWith('/registrations/reg-1')
  })

  it('cancelRegistration DELETEs by id', async () => {
    await cancelRegistration('reg-1')
    expect(apiClient.delete).toHaveBeenCalledWith('/registrations/reg-1')
  })

  it('getWaitlistPositions gets /registrations/waitlist', async () => {
    await getWaitlistPositions()
    expect(apiClient.get).toHaveBeenCalledWith('/registrations/waitlist')
  })
})
