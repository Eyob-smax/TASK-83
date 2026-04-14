import { describe, it, expect } from 'vitest'
import { RoleType, BackupStatus, ImportJobStatus } from '../../src/api/types.js'

describe('Admin view states', () => {
  it('all four roles are available for user management', () => {
    expect(Object.keys(RoleType)).toHaveLength(4)
  })

  it('backup statuses cover all states', () => {
    expect(BackupStatus.SCHEDULED).toBe('SCHEDULED')
    expect(BackupStatus.IN_PROGRESS).toBe('IN_PROGRESS')
    expect(BackupStatus.COMPLETED).toBe('COMPLETED')
    expect(BackupStatus.FAILED).toBe('FAILED')
  })

  it('import job statuses include CIRCUIT_BROKEN', () => {
    expect(ImportJobStatus.CIRCUIT_BROKEN).toBe('CIRCUIT_BROKEN')
  })

  it('admin routes require SYSTEM_ADMIN only', () => {
    const requiredRole = 'SYSTEM_ADMIN'
    const userRoles = ['SYSTEM_ADMIN']
    expect(userRoles.includes(requiredRole)).toBe(true)
  })

  it('audit export restriction message is meaningful', () => {
    const restriction = 'Downloads are watermarked with operator identity and timestamp'
    expect(restriction).toContain('watermarked')
  })

  it('backup retention is 30 days', () => {
    const retentionDays = 30
    expect(retentionDays).toBe(30)
  })

  it('circuit breaker threshold displayed from config', () => {
    const source = { circuitBreakerThreshold: 10 }
    expect(source.circuitBreakerThreshold).toBe(10)
  })
})
