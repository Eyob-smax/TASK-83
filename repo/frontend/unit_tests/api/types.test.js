import { describe, it, expect } from 'vitest'
import {
  unwrapResponse,
  unwrapPagedResponse
} from '../../src/api/types.js'

describe('unwrapResponse', () => {
  it('returns data on success', () => {
    const mockAxios = { data: { success: true, data: { id: '123' } } }
    const result = unwrapResponse(mockAxios)
    expect(result).toEqual({ id: '123' })
  })

  it('throws on failure with errors', () => {
    const mockAxios = {
      data: {
        success: false,
        message: 'Conflict',
        errors: [{ code: 'DUPLICATE_REGISTRATION', message: 'Already registered' }]
      }
    }
    expect(() => unwrapResponse(mockAxios)).toThrow('Conflict')
  })

  it('attaches conflictType from first error code', () => {
    const mockAxios = {
      data: {
        success: false,
        message: 'fail',
        errors: [{ code: 'QUOTA_EXCEEDED', message: 'Full' }]
      }
    }
    try {
      unwrapResponse(mockAxios)
    } catch (e) {
      expect(e.conflictType).toBe('QUOTA_EXCEEDED')
    }
  })

  it('throws with default message when envelope has no message', () => {
    const mockAxios = { data: { success: false } }
    expect(() => unwrapResponse(mockAxios)).toThrow('Request failed')
  })

  it('attaches empty errors array when envelope has no errors', () => {
    const mockAxios = { data: { success: false, message: 'x' } }
    try { unwrapResponse(mockAxios) } catch (e) {
      expect(e.errors).toEqual([])
    }
  })
})

describe('unwrapPagedResponse', () => {
  it('returns paged data on success', () => {
    const mockAxios = {
      data: {
        success: true,
        data: { content: [1, 2], page: 0, size: 10, totalElements: 2, totalPages: 1 }
      }
    }
    const result = unwrapPagedResponse(mockAxios)
    expect(result.content).toHaveLength(2)
    expect(result.totalElements).toBe(2)
  })

  it('throws on failure', () => {
    const mockAxios = { data: { success: false, message: 'Error' } }
    expect(() => unwrapPagedResponse(mockAxios)).toThrow('Error')
  })

  it('throws with default message when envelope has no message', () => {
    const mockAxios = { data: { success: false } }
    expect(() => unwrapPagedResponse(mockAxios)).toThrow('Request failed')
  })
})
