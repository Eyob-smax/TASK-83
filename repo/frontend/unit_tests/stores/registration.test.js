import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useRegistrationStore } from '../../src/stores/registration.js'

vi.mock('../../src/api/registration.js', () => ({
  register: vi.fn(),
  listRegistrations: vi.fn(),
  cancelRegistration: vi.fn(),
  getWaitlistPositions: vi.fn()
}))

import { register, listRegistrations, getWaitlistPositions } from '../../src/api/registration.js'

describe('registration store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('registerForSession returns CONFIRMED on success', async () => {
    register.mockResolvedValue({
      data: { data: { id: 'r1', status: 'CONFIRMED', waitlistPosition: null } }
    })
    const store = useRegistrationStore()
    const result = await store.registerForSession('session-1')
    expect(result.status).toBe('CONFIRMED')
  })

  it('registerForSession sets conflictMessage on DUPLICATE', async () => {
    register.mockRejectedValue({
      response: { data: { message: 'Already registered', errors: [{ code: 'DUPLICATE_REGISTRATION' }] } }
    })
    const store = useRegistrationStore()
    await expect(store.registerForSession('s1')).rejects.toThrow()
    expect(store.conflictMessage).toBe('Already registered')
  })

  it('registerForSession sets error for other failures', async () => {
    register.mockRejectedValue({
      response: { data: { message: 'Session not found', errors: [{ code: 'NOT_FOUND' }] } }
    })
    const store = useRegistrationStore()
    await expect(store.registerForSession('s1')).rejects.toThrow()
    expect(store.error).toBe('Session not found')
  })

  it('fetchWaitlist populates entries', async () => {
    getWaitlistPositions.mockResolvedValue({
      data: { data: [{ id: 'w1', waitlistPosition: 2, sessionTitle: 'Test' }] }
    })
    const store = useRegistrationStore()
    await store.fetchWaitlist()
    expect(store.waitlistEntries).toHaveLength(1)
    expect(store.waitlistEntries[0].waitlistPosition).toBe(2)
  })
})
