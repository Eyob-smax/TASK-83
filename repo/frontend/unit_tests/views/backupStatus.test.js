import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('../../src/api/admin.js', () => ({
  listUsers: vi.fn(), createUser: vi.fn(), updateUser: vi.fn(),
  getSecuritySettings: vi.fn(), updateSecuritySettings: vi.fn(),
  listAuditLogs: vi.fn(), getAuditLog: vi.fn(), exportAuditLogs: vi.fn(), downloadAuditExport: vi.fn(),
  listBackups: vi.fn(), getBackup: vi.fn(), triggerBackup: vi.fn(), getRetentionStatus: vi.fn()
}))
vi.mock('../../src/api/imports.js', () => ({
  listSources: vi.fn(), createSource: vi.fn(), updateSource: vi.fn(), listJobs: vi.fn(), getJob: vi.fn(), triggerCrawl: vi.fn(), getCircuitBreakerStatus: vi.fn()
}))
vi.mock('../../src/api/exports.js', () => ({
  exportRoster: vi.fn(), exportFinanceReport: vi.fn(), downloadExport: vi.fn(), getExportPolicies: vi.fn(), updateExportPolicies: vi.fn()
}))

import { listBackups, getRetentionStatus, triggerBackup } from '../../src/api/admin.js'
import BackupStatusView from '../../src/views/admin/BackupStatusView.vue'
import { useAuthStore } from '../../src/stores/auth.js'

async function mountView({ role = 'SYSTEM_ADMIN' } = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  auth.user = { id: 'u1', roleType: role }
  return mount(BackupStatusView, { global: { plugins: [pinia] } })
}

describe('BackupStatusView', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('denies access for non-admin users', async () => {
    const wrapper = await mountView({ role: 'ATTENDEE' })
    expect(wrapper.text()).toContain('Access Denied')
  })

  it('shows metrics cards with totals after load', async () => {
    listBackups.mockResolvedValue({
      data: { data: [
        { id: 'b1', status: 'COMPLETED', fileSize: 2048 },
        { id: 'b2', status: 'FAILED', fileSize: 0 },
        { id: 'b3', status: 'COMPLETED', fileSize: 1048576 }
      ] }
    })
    getRetentionStatus.mockResolvedValue({ data: { data: { retentionDays: 30 } } })
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.find('.metrics-cards').exists()).toBe(true)
    expect(wrapper.text()).toContain('30 days')
    // Completed count is 2, Failed is 1
    const values = wrapper.findAll('.metric-value').map(e => e.text())
    expect(values).toContain('3')
    expect(values).toContain('2')
    expect(values).toContain('1')
  })

  it('triggers backup when button clicked', async () => {
    listBackups.mockResolvedValue({ data: { data: [] } })
    getRetentionStatus.mockResolvedValue({ data: { data: { retentionDays: 30 } } })
    triggerBackup.mockResolvedValue({ data: { data: { id: 'b-new', status: 'SCHEDULED' } } })
    const wrapper = await mountView()
    await flushPromises()
    await wrapper.findAll('button').find(b => b.text().includes('Trigger Backup')).trigger('click')
    await flushPromises()
    expect(triggerBackup).toHaveBeenCalled()
  })

  it('renders empty state when no backups', async () => {
    listBackups.mockResolvedValue({ data: { data: [] } })
    getRetentionStatus.mockResolvedValue({ data: { data: { retentionDays: 30 } } })
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('No backup records found')
  })

  it('renders a row for each backup in history', async () => {
    listBackups.mockResolvedValue({
      data: { data: [
        { id: 'b1', status: 'COMPLETED', fileSize: 2048, createdAt: '2026-04-01T00:00:00Z' }
      ] }
    })
    getRetentionStatus.mockResolvedValue({ data: { data: { retentionDays: 30 } } })
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.find('.data-table').exists()).toBe(true)
    expect(wrapper.text()).toContain('COMPLETED')
  })

  it('formats file size via KB/MB ranges', async () => {
    listBackups.mockResolvedValue({
      data: { data: [
        { id: 'b1', status: 'COMPLETED', fileSize: 512 },
        { id: 'b2', status: 'COMPLETED', fileSize: 2048 },
        { id: 'b3', status: 'COMPLETED', fileSize: 2 * 1024 * 1024 }
      ] }
    })
    getRetentionStatus.mockResolvedValue({ data: { data: { retentionDays: 30 } } })
    const wrapper = await mountView()
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('512 B')
    expect(text).toContain('2.0 KB')
    expect(text).toContain('2.0 MB')
  })
})
