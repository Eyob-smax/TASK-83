import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '../../src/stores/auth.js'

describe('auth store', () => {
  beforeEach(() => {
    sessionStorage.clear()
    setActivePinia(createPinia())
  })

  it('initializes with null user and token', () => {
    const auth = useAuthStore()
    expect(auth.user).toBeNull()
    expect(auth.token).toBeNull()
    expect(auth.isAuthenticated).toBe(false)
  })

  it('hasRole checks roleType values', () => {
    const auth = useAuthStore()
    auth.user = { roleType: 'SYSTEM_ADMIN' }
    expect(auth.hasRole('SYSTEM_ADMIN')).toBe(true)
    expect(auth.hasRole('EVENT_STAFF')).toBe(false)
  })

  it('hasAnyRole matches any configured role', () => {
    const auth = useAuthStore()
    auth.user = { roleType: 'FINANCE_MANAGER' }
    expect(auth.hasAnyRole(['EVENT_STAFF', 'FINANCE_MANAGER'])).toBe(true)
    expect(auth.hasAnyRole(['ATTENDEE', 'SYSTEM_ADMIN'])).toBe(false)
  })

  it('initFromSession restores stored user and token', () => {
    sessionStorage.setItem('eventops_user', JSON.stringify({ id: 'u1', roleType: 'ATTENDEE' }))
    sessionStorage.setItem('eventops_token', 'optional-token')

    const auth = useAuthStore()
    auth.initFromSession()

    expect(auth.user.id).toBe('u1')
    expect(auth.token).toBe('optional-token')
    expect(auth.isAuthenticated).toBe(true)
  })
})
