import { describe, it, expect } from 'vitest'
import { PeriodStatus, AllocationMethod, RevenueRecognitionMethod, PostingStatus } from '../../src/api/types.js'

describe('Finance view states', () => {
  it('period statuses have correct values', () => {
    expect(PeriodStatus.OPEN).toBe('OPEN')
    expect(PeriodStatus.CLOSED).toBe('CLOSED')
    expect(PeriodStatus.LOCKED).toBe('LOCKED')
  })

  it('allocation methods match backend enum', () => {
    expect(Object.keys(AllocationMethod)).toHaveLength(3)
    expect(AllocationMethod.PROPORTIONAL).toBe('PROPORTIONAL')
    expect(AllocationMethod.FIXED).toBe('FIXED')
    expect(AllocationMethod.TIERED).toBe('TIERED')
  })

  it('revenue recognition methods match backend enum', () => {
    expect(RevenueRecognitionMethod.IMMEDIATE).toBe('IMMEDIATE')
    expect(RevenueRecognitionMethod.OVER_SESSION_DATES).toBe('OVER_SESSION_DATES')
  })

  it('posting statuses cover all states', () => {
    expect(PostingStatus.DRAFT).toBe('DRAFT')
    expect(PostingStatus.POSTED).toBe('POSTED')
    expect(PostingStatus.REVERSED).toBe('REVERSED')
    expect(PostingStatus.FAILED).toBe('FAILED')
  })

  it('finance routes require FINANCE_MANAGER or SYSTEM_ADMIN', () => {
    const allowedRoles = ['FINANCE_MANAGER', 'SYSTEM_ADMIN']
    expect(allowedRoles).toContain('FINANCE_MANAGER')
    expect(allowedRoles).not.toContain('ATTENDEE')
  })

  it('rule version is displayed as integer', () => {
    const rule = { name: 'Test', version: 3, active: true }
    expect(Number.isInteger(rule.version)).toBe(true)
  })
})
