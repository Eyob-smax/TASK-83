import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAdminStore } from '../../src/stores/admin.js'

vi.mock('../../src/api/admin.js', () => ({
  listUsers: vi.fn(),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  getSecuritySettings: vi.fn(),
  updateSecuritySettings: vi.fn(),
  listAuditLogs: vi.fn(),
  getAuditLog: vi.fn(),
  exportAuditLogs: vi.fn(),
  downloadAuditExport: vi.fn(),
  listBackups: vi.fn(),
  getBackup: vi.fn(),
  triggerBackup: vi.fn(),
  getRetentionStatus: vi.fn()
}))
vi.mock('../../src/api/imports.js', () => ({
  listSources: vi.fn(),
  listJobs: vi.fn(),
  triggerCrawl: vi.fn(),
  getCircuitBreakerStatus: vi.fn(),
  createSource: vi.fn(),
  updateSource: vi.fn(),
  getJob: vi.fn()
}))
vi.mock('../../src/api/exports.js', () => ({
  exportRoster: vi.fn(),
  exportFinanceReport: vi.fn(),
  downloadExport: vi.fn(),
  getExportPolicies: vi.fn(),
  updateExportPolicies: vi.fn()
}))

import {
  listAuditLogs,
  triggerBackup,
  listUsers,
  updateSecuritySettings,
  createUser,
  updateUser,
  getAuditLog,
  exportAuditLogs,
  listBackups,
  getSecuritySettings,
  getRetentionStatus
} from '../../src/api/admin.js'
import { listSources, getCircuitBreakerStatus, listJobs, triggerCrawl } from '../../src/api/imports.js'
import { getExportPolicies, updateExportPolicies, exportRoster, exportFinanceReport } from '../../src/api/exports.js'

describe('admin store', () => {
  beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks() })

  it('initializes with empty arrays', () => {
    const store = useAdminStore()
    expect(store.users).toEqual([])
    expect(store.auditLogs).toEqual([])
    expect(store.backups).toEqual([])
  })

  it('fetchAuditLogs populates paginated results', async () => {
    listAuditLogs.mockResolvedValue({
      data: { data: { content: [{ id: 'a1', actionType: 'LOGIN_SUCCESS' }], page: 0, totalPages: 1, totalElements: 1 } }
    })
    const store = useAdminStore()
    await store.fetchAuditLogs({})
    expect(store.auditLogs).toHaveLength(1)
    expect(store.auditPagination.totalElements).toBe(1)
  })

  it('triggerBackup adds to list and sets success', async () => {
    triggerBackup.mockResolvedValue({ data: { data: { id: 'b1', status: 'IN_PROGRESS' } } })
    const store = useAdminStore()
    await store.triggerBackup()
    expect(store.backups).toHaveLength(1)
    expect(store.successMessage).toBe('Backup triggered')
  })

  it('fetchUsers handles paged user responses', async () => {
    listUsers.mockResolvedValue({ data: { data: { content: [{ id: 'u1', username: 'admin', roleType: 'SYSTEM_ADMIN' }] } } })
    const store = useAdminStore()
    await store.fetchUsers()
    expect(store.users[0].roleType).toBe('SYSTEM_ADMIN')
  })

  it('updateSecuritySettings stores latest response', async () => {
    updateSecuritySettings.mockResolvedValue({ data: { data: { rateLimitPerMinute: 120, signatureEnabled: true } } })
    const store = useAdminStore()
    await store.updateSecuritySettings({ rateLimitPerMinute: 120 })
    expect(store.securitySettings.rateLimitPerMinute).toBe(120)
    expect(store.successMessage).toBe('Security settings updated')
  })

  it('fetchImportSources populates sources', async () => {
    listSources.mockResolvedValue({ data: { data: [{ id: 's1', name: 'Badge CSV', active: true }] } })
    const store = useAdminStore()
    await store.fetchImportSources()
    expect(store.importSources).toHaveLength(1)
  })

  it('fetchCircuitBreakerStatus populates status', async () => {
    getCircuitBreakerStatus.mockResolvedValue({ data: { data: { sources: [] } } })
    const store = useAdminStore()
    await store.fetchCircuitBreakerStatus()
    expect(store.circuitBreakerStatus).toBeTruthy()
  })

  it('fetchUsers sets error on failure', async () => {
    listUsers.mockRejectedValue({ response: { data: { message: 'fail' } } })
    const store = useAdminStore()
    await store.fetchUsers()
    expect(store.error).toBe('fail')
  })

  it('createUser appends user on success', async () => {
    createUser.mockResolvedValue({ data: { data: { id: 'u-new', username: 'new' } } })
    const store = useAdminStore()
    await store.createUser({ username: 'new' })
    expect(store.users).toHaveLength(1)
    expect(store.successMessage).toBe('User created')
  })

  it('createUser sets error on failure', async () => {
    createUser.mockRejectedValue({ response: { data: { message: 'duplicate' } } })
    const store = useAdminStore()
    await store.createUser({ username: 'x' })
    expect(store.error).toBe('duplicate')
  })

  it('updateUser replaces matching user in list', async () => {
    updateUser.mockResolvedValue({ data: { data: { id: 'u1', status: 'DISABLED' } } })
    const store = useAdminStore()
    store.users = [{ id: 'u1', status: 'ACTIVE' }]
    await store.updateUser('u1', { status: 'DISABLED' })
    expect(store.users[0].status).toBe('DISABLED')
  })

  it('fetchAuditLog populates currentAuditLog', async () => {
    getAuditLog.mockResolvedValue({ data: { data: { id: 'a1', actionType: 'LOGIN' } } })
    const store = useAdminStore()
    await store.fetchAuditLog('a1')
    expect(store.currentAuditLog.id).toBe('a1')
  })

  it('triggerAuditExport returns export result', async () => {
    exportAuditLogs.mockResolvedValue({ data: { data: { exportId: 'e1' } } })
    const store = useAdminStore()
    const res = await store.triggerAuditExport({ dateFrom: '2026-01-01' })
    expect(res.exportId).toBe('e1')
    expect(store.successMessage).toBe('Export queued')
  })

  it('triggerAuditExport throws on failure and sets error', async () => {
    exportAuditLogs.mockRejectedValue({ response: { data: { message: 'boom' } } })
    const store = useAdminStore()
    await expect(store.triggerAuditExport({})).rejects.toBeTruthy()
    expect(store.error).toBe('boom')
  })

  it('fetchBackups populates backups', async () => {
    listBackups.mockResolvedValue({ data: { data: [{ id: 'b1' }, { id: 'b2' }] } })
    const store = useAdminStore()
    await store.fetchBackups()
    expect(store.backups).toHaveLength(2)
  })

  it('fetchRetentionStatus populates status silently', async () => {
    getRetentionStatus.mockResolvedValue({ data: { data: { retentionDays: 30 } } })
    const store = useAdminStore()
    await store.fetchRetentionStatus()
    expect(store.retentionStatus.retentionDays).toBe(30)
  })

  it('fetchSecuritySettings populates settings', async () => {
    getSecuritySettings.mockResolvedValue({ data: { data: { rateLimitPerMinute: 60 } } })
    const store = useAdminStore()
    await store.fetchSecuritySettings()
    expect(store.securitySettings.rateLimitPerMinute).toBe(60)
  })

  it('fetchImportJobs handles paged response', async () => {
    listJobs.mockResolvedValue({ data: { data: { content: [{ id: 'j1' }] } } })
    const store = useAdminStore()
    await store.fetchImportJobs({})
    expect(store.importJobs).toHaveLength(1)
  })

  it('triggerImportCrawl returns result', async () => {
    triggerCrawl.mockResolvedValue({ data: { data: { jobId: 'new' } } })
    const store = useAdminStore()
    const res = await store.triggerImportCrawl({ sourceId: 's1' })
    expect(res.jobId).toBe('new')
    expect(store.successMessage).toBe('Crawl triggered')
  })

  it('triggerImportCrawl throws on failure', async () => {
    triggerCrawl.mockRejectedValue({ response: { data: { message: 'err' } } })
    const store = useAdminStore()
    await expect(store.triggerImportCrawl({})).rejects.toBeTruthy()
    expect(store.error).toBe('err')
  })

  it('fetchExportPolicies populates policies', async () => {
    getExportPolicies.mockResolvedValue({ data: { data: [{ id: 'pol-1' }] } })
    const store = useAdminStore()
    await store.fetchExportPolicies()
    expect(store.exportPolicies).toHaveLength(1)
  })

  it('updateExportPolicy sets success message', async () => {
    updateExportPolicies.mockResolvedValue({ data: {} })
    const store = useAdminStore()
    await store.updateExportPolicy({ reportType: 'ROSTER' })
    expect(store.successMessage).toBe('Policy updated')
  })

  it('requestExport for roster returns result', async () => {
    exportRoster.mockResolvedValue({ data: { data: { id: 'exp-1' } } })
    const store = useAdminStore()
    const res = await store.requestExport('roster', { sessionId: 's1' })
    expect(res.id).toBe('exp-1')
  })

  it('requestExport for finance returns result', async () => {
    exportFinanceReport.mockResolvedValue({ data: { data: { id: 'exp-2' } } })
    const store = useAdminStore()
    const res = await store.requestExport('finance', { periodId: 'p1' })
    expect(res.id).toBe('exp-2')
  })

  it('requestExport throws on failure', async () => {
    exportRoster.mockRejectedValue({ response: { data: { message: 'fail' } } })
    const store = useAdminStore()
    await expect(store.requestExport('roster', {})).rejects.toBeTruthy()
    expect(store.error).toBe('fail')
  })

  it('clearMessages resets error and successMessage', () => {
    const store = useAdminStore()
    store.error = 'e'
    store.successMessage = 's'
    store.clearMessages()
    expect(store.error).toBeNull()
    expect(store.successMessage).toBeNull()
  })
})
