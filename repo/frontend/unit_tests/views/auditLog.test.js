import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'

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
  listSources: vi.fn(), createSource: vi.fn(), updateSource: vi.fn(), listJobs: vi.fn(), getJob: vi.fn(), triggerCrawl: vi.fn(), getCircuitBreakerStatus: vi.fn()
}))

vi.mock('../../src/api/exports.js', () => ({
  exportRoster: vi.fn(), exportFinanceReport: vi.fn(), downloadExport: vi.fn(), getExportPolicies: vi.fn(), updateExportPolicies: vi.fn()
}))

import { listAuditLogs, exportAuditLogs } from '../../src/api/admin.js'
import AuditLogView from '../../src/views/admin/AuditLogView.vue'
import { useAuthStore } from '../../src/stores/auth.js'

async function mountView({ role = 'SYSTEM_ADMIN' } = {}) {
  setActivePinia(createPinia())
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: AuditLogView }]
  })
  await router.push('/')
  await router.isReady()
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  auth.user = { id: 'u1', roleType: role }
  return mount(AuditLogView, { global: { plugins: [pinia, router] } })
}

describe('AuditLogView', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('shows access denied for non-admin users', async () => {
    const wrapper = await mountView({ role: 'ATTENDEE' })
    expect(wrapper.text()).toContain('Access Denied')
  })

  it('renders filter bar for admin users', async () => {
    listAuditLogs.mockResolvedValue({ data: { data: { content: [], page: 0, totalPages: 0, totalElements: 0 } } })
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.find('#filter-from').exists()).toBe(true)
    expect(wrapper.find('#filter-action').exists()).toBe(true)
  })

  it('triggers fetch when Search button is clicked', async () => {
    listAuditLogs.mockResolvedValue({ data: { data: { content: [], page: 0, totalPages: 0, totalElements: 0 } } })
    const wrapper = await mountView()
    await flushPromises()
    listAuditLogs.mockClear()
    await wrapper.findAll('button').find(b => b.text().includes('Search')).trigger('click')
    await flushPromises()
    expect(listAuditLogs).toHaveBeenCalled()
  })

  it('renders log rows when results present', async () => {
    listAuditLogs.mockResolvedValue({
      data: {
        data: {
          content: [
            { id: 'l1', actionType: 'USER_LOGIN', operatorId: 'u1', entityType: 'USER', entityId: 'x', description: 'ok', timestamp: '2026-04-01T10:00:00Z' }
          ],
          page: 0, totalPages: 1, totalElements: 1
        }
      }
    })
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.find('.data-table').exists()).toBe(true)
    expect(wrapper.text()).toContain('USER LOGIN')
    expect(wrapper.text()).toContain('u1')
  })

  it('shows empty state when no logs returned', async () => {
    listAuditLogs.mockResolvedValue({ data: { data: { content: [], page: 0, totalPages: 0, totalElements: 0 } } })
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('No audit log entries found')
  })

  it('Export CSV button triggers exportAuditLogs', async () => {
    listAuditLogs.mockResolvedValue({ data: { data: { content: [], page: 0, totalPages: 0, totalElements: 0 } } })
    exportAuditLogs.mockResolvedValue({ data: { data: { exportId: 'exp-1' } } })
    const wrapper = await mountView()
    await flushPromises()
    await wrapper.findAll('button').find(b => b.text().includes('Export CSV')).trigger('click')
    await flushPromises()
    expect(exportAuditLogs).toHaveBeenCalled()
    expect(wrapper.find('.download-link').exists()).toBe(true)
  })

  it('changing filters and searching passes actionType param', async () => {
    listAuditLogs.mockResolvedValue({ data: { data: { content: [], page: 0, totalPages: 0, totalElements: 0 } } })
    const wrapper = await mountView()
    await flushPromises()
    await wrapper.find('#filter-action').setValue('USER_LOGIN')
    listAuditLogs.mockClear()
    await wrapper.findAll('button').find(b => b.text().includes('Search')).trigger('click')
    await flushPromises()
    expect(listAuditLogs).toHaveBeenCalled()
    const params = listAuditLogs.mock.calls[0][0]
    expect(params.actionType).toBe('USER_LOGIN')
  })
})
