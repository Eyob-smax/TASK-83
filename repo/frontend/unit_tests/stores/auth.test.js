import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('../../src/api/auth.js', () => ({
  login: vi.fn(),
  logout: vi.fn(),
  getCurrentUser: vi.fn(),
  refreshSession: vi.fn(),
  createAccount: vi.fn()
}))

import { login as apiLogin, logout as apiLogout, getCurrentUser, refreshSession as apiRefresh } from '../../src/api/auth.js'
import { useAuthStore } from '../../src/stores/auth.js'

describe('auth store', () => {
  beforeEach(() => {
    sessionStorage.clear()
    setActivePinia(createPinia())
    vi.clearAllMocks()
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

  it('initFromSession silently ignores malformed user JSON', () => {
    sessionStorage.setItem('eventops_user', '{bad}')
    const auth = useAuthStore()
    auth.initFromSession()
    expect(auth.user).toBeNull()
  })

  it('userRoles returns array of current roleType', () => {
    const auth = useAuthStore()
    auth.user = { roleType: 'ATTENDEE' }
    expect(auth.userRoles).toEqual(['ATTENDEE'])
  })

  it('userRoles is empty when user is null', () => {
    const auth = useAuthStore()
    expect(auth.userRoles).toEqual([])
  })

  it('login populates user and token from response', async () => {
    apiLogin.mockResolvedValue({
      data: { success: true, data: { user: { id: 'u1', roleType: 'ATTENDEE' } } },
      headers: { 'x-session-signature-token': 'tok-1' }
    })
    const auth = useAuthStore()
    await auth.login({ username: 'u', password: 'p' })
    expect(auth.user.id).toBe('u1')
    expect(auth.token).toBe('tok-1')
    expect(sessionStorage.getItem('eventops_token')).toBe('tok-1')
  })

  it('login clears state on error', async () => {
    apiLogin.mockRejectedValue({ response: { status: 401 } })
    const auth = useAuthStore()
    await expect(auth.login({ username: 'u', password: 'bad' })).rejects.toBeTruthy()
    expect(auth.user).toBeNull()
    expect(auth.token).toBeNull()
  })

  it('logout clears state even when API fails', async () => {
    apiLogout.mockRejectedValue(new Error('network'))
    const auth = useAuthStore()
    auth.user = { id: 'u1' }
    auth.token = 'tok'
    await auth.logout()
    expect(auth.user).toBeNull()
    expect(auth.token).toBeNull()
    expect(sessionStorage.getItem('eventops_user')).toBeNull()
  })

  it('fetchCurrentUser populates user on success', async () => {
    getCurrentUser.mockResolvedValue({ data: { success: true, data: { id: 'u9', roleType: 'SYSTEM_ADMIN' } } })
    const auth = useAuthStore()
    const data = await auth.fetchCurrentUser()
    expect(data.id).toBe('u9')
    expect(auth.user.id).toBe('u9')
  })

  it('fetchCurrentUser clears state on failure and returns null', async () => {
    getCurrentUser.mockRejectedValue({ response: { status: 401 } })
    const auth = useAuthStore()
    auth.user = { id: 'x' }
    const data = await auth.fetchCurrentUser()
    expect(data).toBeNull()
    expect(auth.user).toBeNull()
  })

  it('refreshSession updates token when server returns new one', async () => {
    apiRefresh.mockResolvedValue({
      data: { success: true, data: { id: 'u1' } },
      headers: { 'x-session-signature-token': 'new-tok' }
    })
    const auth = useAuthStore()
    await auth.refreshSession()
    expect(auth.token).toBe('new-tok')
  })

  it('refreshSession clears state on error and returns null', async () => {
    apiRefresh.mockRejectedValue({ response: { status: 401 } })
    const auth = useAuthStore()
    auth.user = { id: 'u1' }
    const data = await auth.refreshSession()
    expect(data).toBeNull()
    expect(auth.user).toBeNull()
  })
})
