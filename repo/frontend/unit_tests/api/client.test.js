import { beforeEach, describe, expect, it, vi } from 'vitest'

const interceptorState = {
  request: null,
  response: null
}

const axiosInstance = {
  interceptors: {
    request: {
      use: vi.fn((fulfilled, rejected) => {
        interceptorState.request = { fulfilled, rejected }
      })
    },
    response: {
      use: vi.fn((fulfilled, rejected) => {
        interceptorState.response = { fulfilled, rejected }
      })
    }
  }
}

const computeSignature = vi.fn(async () => 'signed-value')
const generateNonce = vi.fn(() => 'nonce-123')
const generateTimestamp = vi.fn(() => '2026-04-14T10:00:00Z')

vi.mock('axios', () => ({
  default: {
    create: vi.fn(() => axiosInstance)
  }
}))

vi.mock('@/utils/signature', () => ({
  computeSignature,
  generateNonce,
  generateTimestamp
}))

describe('api client interceptors', () => {
  beforeEach(async () => {
    sessionStorage.clear()
    computeSignature.mockClear()
    generateNonce.mockClear()
    generateTimestamp.mockClear()
    interceptorState.request = null
    interceptorState.response = null
    vi.resetModules()
    await import('../../src/api/client.js')
  })

  it('signs authenticated GET requests', async () => {
    sessionStorage.setItem('eventops_token', 'session-secret')

    const config = await interceptorState.request.fulfilled({
      url: '/auth/me',
      method: 'get',
      headers: {}
    })

    expect(generateTimestamp).toHaveBeenCalled()
    expect(generateNonce).toHaveBeenCalled()
    expect(computeSignature).toHaveBeenCalledWith('', '2026-04-14T10:00:00Z', 'nonce-123', 'session-secret')
    expect(config.headers['X-Request-Timestamp']).toBe('2026-04-14T10:00:00Z')
    expect(config.headers['X-Request-Nonce']).toBe('nonce-123')
    expect(config.headers['X-Request-Signature']).toBe('signed-value')
  })

  it('does not sign login requests even when a session token exists', async () => {
    sessionStorage.setItem('eventops_token', 'session-secret')

    const config = await interceptorState.request.fulfilled({
      url: '/auth/login',
      method: 'post',
      headers: {}
    })

    expect(computeSignature).not.toHaveBeenCalled()
    expect(config.headers['X-Request-Signature']).toBeUndefined()
  })

  it('clears the session signature token on 401 responses', async () => {
    sessionStorage.setItem('eventops_token', 'session-secret')
    sessionStorage.setItem('eventops_user', '{"id":"u1"}')
    window.history.pushState({}, '', '/login')

    await expect(
      interceptorState.response.rejected({
        response: { status: 401 }
      })
    ).rejects.toEqual({
      response: { status: 401 }
    })

    expect(sessionStorage.getItem('eventops_token')).toBeNull()
    expect(sessionStorage.getItem('eventops_user')).toBeNull()
  })
})
