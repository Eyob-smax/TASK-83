import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../../src/api/client.js', () => ({
  default: {
    post: vi.fn(() => Promise.resolve({ data: {} })),
    get: vi.fn(() => Promise.resolve({ data: {} })),
    put: vi.fn(() => Promise.resolve({ data: {} })),
    patch: vi.fn(() => Promise.resolve({ data: {} })),
    delete: vi.fn(() => Promise.resolve({ data: {} }))
  }
}))

import apiClient from '../../src/api/client.js'
import { login, logout, createAccount, refreshSession, getCurrentUser } from '../../src/api/auth.js'

describe('api/auth', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('login posts credentials to /auth/login', async () => {
    const creds = { username: 'u1', password: 'p1' }
    await login(creds)
    expect(apiClient.post).toHaveBeenCalledWith('/auth/login', creds)
  })

  it('logout posts to /auth/logout with no body', async () => {
    await logout()
    expect(apiClient.post).toHaveBeenCalledWith('/auth/logout')
  })

  it('createAccount posts userData to /auth/register', async () => {
    const data = { username: 'u2', password: 'p2' }
    await createAccount(data)
    expect(apiClient.post).toHaveBeenCalledWith('/auth/register', data)
  })

  it('refreshSession posts to /auth/refresh', async () => {
    await refreshSession()
    expect(apiClient.post).toHaveBeenCalledWith('/auth/refresh')
  })

  it('getCurrentUser gets /auth/me', async () => {
    await getCurrentUser()
    expect(apiClient.get).toHaveBeenCalledWith('/auth/me')
  })
})
