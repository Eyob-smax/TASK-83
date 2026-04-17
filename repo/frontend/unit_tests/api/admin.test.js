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
import {
  listUsers,
  createUser,
  updateUser,
  getSecuritySettings,
  updateSecuritySettings,
  listAuditLogs,
  getAuditLog,
  exportAuditLogs,
  downloadAuditExport,
  listBackups,
  getBackup,
  triggerBackup,
  getRetentionStatus
} from '../../src/api/admin.js'

describe('api/admin', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('listUsers forwards params', async () => {
    await listUsers({ page: 0, size: 20 })
    expect(apiClient.get).toHaveBeenCalledWith('/admin/users', { params: { page: 0, size: 20 } })
  })

  it('createUser posts body to /admin/users', async () => {
    const data = { username: 'u1', roleType: 'ATTENDEE' }
    await createUser(data)
    expect(apiClient.post).toHaveBeenCalledWith('/admin/users', data)
  })

  it('updateUser puts body to /admin/users/{id}', async () => {
    const data = { displayName: 'New Name' }
    await updateUser('user-1', data)
    expect(apiClient.put).toHaveBeenCalledWith('/admin/users/user-1', data)
  })

  it('getSecuritySettings gets /admin/security/settings', async () => {
    await getSecuritySettings()
    expect(apiClient.get).toHaveBeenCalledWith('/admin/security/settings')
  })

  it('updateSecuritySettings puts /admin/security/settings', async () => {
    const data = { rateLimitPerMinute: 60 }
    await updateSecuritySettings(data)
    expect(apiClient.put).toHaveBeenCalledWith('/admin/security/settings', data)
  })

  it('listAuditLogs forwards params to /audit/logs', async () => {
    await listAuditLogs({ actionType: 'LOGIN' })
    expect(apiClient.get).toHaveBeenCalledWith('/audit/logs', { params: { actionType: 'LOGIN' } })
  })

  it('getAuditLog gets /audit/logs/{id}', async () => {
    await getAuditLog('log-1')
    expect(apiClient.get).toHaveBeenCalledWith('/audit/logs/log-1')
  })

  it('exportAuditLogs posts params to /audit/logs/export', async () => {
    const params = { dateFrom: '2026-01-01' }
    await exportAuditLogs(params)
    expect(apiClient.post).toHaveBeenCalledWith('/audit/logs/export', params)
  })

  it('downloadAuditExport gets /audit/exports/{id}/download with blob responseType', async () => {
    await downloadAuditExport('exp-1')
    expect(apiClient.get).toHaveBeenCalledWith('/audit/exports/exp-1/download', { responseType: 'blob' })
  })

  it('listBackups forwards params to /admin/backups', async () => {
    await listBackups({ status: 'COMPLETED' })
    expect(apiClient.get).toHaveBeenCalledWith('/admin/backups', { params: { status: 'COMPLETED' } })
  })

  it('getBackup gets /admin/backups/{id}', async () => {
    await getBackup('b-1')
    expect(apiClient.get).toHaveBeenCalledWith('/admin/backups/b-1')
  })

  it('triggerBackup posts /admin/backups/trigger', async () => {
    await triggerBackup()
    expect(apiClient.post).toHaveBeenCalledWith('/admin/backups/trigger')
  })

  it('getRetentionStatus gets /admin/backups/retention', async () => {
    await getRetentionStatus()
    expect(apiClient.get).toHaveBeenCalledWith('/admin/backups/retention')
  })

  it('listUsers called without params returns resolved promise', async () => {
    const result = await listUsers()
    expect(result).toBeDefined()
    expect(apiClient.get).toHaveBeenCalledWith('/admin/users', { params: undefined })
  })
})
