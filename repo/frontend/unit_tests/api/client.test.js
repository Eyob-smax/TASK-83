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

  it('skips signing /auth/register requests even with token', async () => {
    sessionStorage.setItem('eventops_token', 'session-secret')
    const config = await interceptorState.request.fulfilled({
      url: '/auth/register',
      method: 'post',
      headers: {}
    })
    expect(computeSignature).not.toHaveBeenCalled()
    expect(config.headers['X-Request-Signature']).toBeUndefined()
  })

  it('does not attempt signing when no session token', async () => {
    const config = await interceptorState.request.fulfilled({
      url: '/auth/me',
      method: 'get',
      headers: {}
    })
    expect(computeSignature).not.toHaveBeenCalled()
    expect(config.headers['X-Request-Signature']).toBeUndefined()
  })

  it('includes body in signature when config.data is present', async () => {
    sessionStorage.setItem('eventops_token', 'session-secret')
    const config = await interceptorState.request.fulfilled({
      url: '/events',
      method: 'post',
      data: { name: 'x' },
      headers: {}
    })
    expect(computeSignature).toHaveBeenCalled()
    const args = computeSignature.mock.calls[0]
    expect(args[0]).toBe(JSON.stringify({ name: 'x' }))
  })

  it('429 with numeric Retry-After attaches retryAfter as a Number', async () => {
    const err = {
      response: { status: 429, headers: { 'retry-after': '45' } }
    }
    await expect(interceptorState.response.rejected(err)).rejects.toBeTruthy()
    expect(err.retryAfter).toBe(45)
  })

  it('429 with non-numeric Retry-After attaches raw value', async () => {
    const err = {
      response: { status: 429, headers: { 'retry-after': 'Mon, 02 Jan 2026 00:00:00 GMT' } }
    }
    await expect(interceptorState.response.rejected(err)).rejects.toBeTruthy()
    expect(err.retryAfter).toBe('Mon, 02 Jan 2026 00:00:00 GMT')
  })

  it('429 without Retry-After header does not set retryAfter', async () => {
    const err = { response: { status: 429, headers: {} } }
    await expect(interceptorState.response.rejected(err)).rejects.toBeTruthy()
    expect(err.retryAfter).toBeUndefined()
  })

  it('network error (no response) rejects untouched', async () => {
    const err = new Error('timeout')
    await expect(interceptorState.response.rejected(err)).rejects.toBe(err)
  })

  it('non-401 / non-429 status rejects untouched', async () => {
    const err = { response: { status: 500 } }
    await expect(interceptorState.response.rejected(err)).rejects.toBe(err)
  })

  it('request rejected handler rejects with error', async () => {
    const err = new Error('request blocked')
    await expect(interceptorState.request.rejected(err)).rejects.toBe(err)
  })

  it('401 does not redirect when already on /login', async () => {
    window.history.pushState({}, '', '/login')
    const origHref = window.location.href
    const err = { response: { status: 401 } }
    await expect(interceptorState.response.rejected(err)).rejects.toBeTruthy()
    // No redirect should have occurred
    expect(window.location.pathname).toBe('/login')
  })
})
