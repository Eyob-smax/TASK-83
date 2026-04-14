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

import { listAuditLogs, triggerBackup, listUsers, updateSecuritySettings } from '../../src/api/admin.js'
import { listSources, getCircuitBreakerStatus } from '../../src/api/imports.js'

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
})
