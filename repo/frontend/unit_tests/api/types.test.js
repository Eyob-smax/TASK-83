import { describe, it, expect } from 'vitest'
import {
  RoleType,
  SessionStatus,
  RegistrationStatus,
  CheckInStatus,
  NotificationType,
  SendStatus,
  AllocationMethod,
  RevenueRecognitionMethod,
  ConflictType,
  unwrapResponse,
  unwrapPagedResponse
} from '../../src/api/types.js'

describe('RoleType enum', () => {
  it('has exactly 4 roles', () => {
    expect(Object.keys(RoleType)).toHaveLength(4)
  })
  it('includes ATTENDEE', () => {
    expect(RoleType.ATTENDEE).toBe('ATTENDEE')
  })
  it('includes SYSTEM_ADMIN', () => {
    expect(RoleType.SYSTEM_ADMIN).toBe('SYSTEM_ADMIN')
  })
})

describe('SessionStatus enum', () => {
  it('has 7 statuses', () => {
    expect(Object.keys(SessionStatus)).toHaveLength(7)
  })
  it('includes OPEN_FOR_REGISTRATION', () => {
    expect(SessionStatus.OPEN_FOR_REGISTRATION).toBe('OPEN_FOR_REGISTRATION')
  })
})

describe('RegistrationStatus enum', () => {
  it('has 5 statuses', () => {
    expect(Object.keys(RegistrationStatus)).toHaveLength(5)
  })
  it('includes WAITLISTED', () => {
    expect(RegistrationStatus.WAITLISTED).toBe('WAITLISTED')
  })
  it('includes PROMOTED', () => {
    expect(RegistrationStatus.PROMOTED).toBe('PROMOTED')
  })
})

describe('CheckInStatus enum', () => {
  it('has 7 statuses', () => {
    expect(Object.keys(CheckInStatus)).toHaveLength(7)
  })
  it('includes DENIED_WINDOW_CLOSED', () => {
    expect(CheckInStatus.DENIED_WINDOW_CLOSED).toBe('DENIED_WINDOW_CLOSED')
  })
})

describe('NotificationType enum', () => {
  it('has 9 types', () => {
    expect(Object.keys(NotificationType)).toHaveLength(9)
  })
  it('includes WAITLIST_PROMOTION', () => {
    expect(NotificationType.WAITLIST_PROMOTION).toBe('WAITLIST_PROMOTION')
  })
})

describe('AllocationMethod enum', () => {
  it('has 3 methods', () => {
    expect(Object.keys(AllocationMethod)).toHaveLength(3)
  })
  it('values are PROPORTIONAL, FIXED, TIERED', () => {
    expect(AllocationMethod.PROPORTIONAL).toBe('PROPORTIONAL')
    expect(AllocationMethod.FIXED).toBe('FIXED')
    expect(AllocationMethod.TIERED).toBe('TIERED')
  })
})

describe('ConflictType enum', () => {
  it('has 9 conflict types', () => {
    expect(Object.keys(ConflictType)).toHaveLength(9)
  })
  it('includes DUPLICATE_REGISTRATION', () => {
    expect(ConflictType.DUPLICATE_REGISTRATION).toBe('DUPLICATE_REGISTRATION')
  })
  it('includes QUOTA_EXCEEDED', () => {
    expect(ConflictType.QUOTA_EXCEEDED).toBe('QUOTA_EXCEEDED')
  })
})

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
})
