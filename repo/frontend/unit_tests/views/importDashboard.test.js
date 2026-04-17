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
  listSources: vi.fn(),
  createSource: vi.fn(),
  updateSource: vi.fn(),
  listJobs: vi.fn(),
  getJob: vi.fn(),
  triggerCrawl: vi.fn(),
  getCircuitBreakerStatus: vi.fn()
}))

vi.mock('../../src/api/exports.js', () => ({
  exportRoster: vi.fn(), exportFinanceReport: vi.fn(),
  downloadExport: vi.fn(), getExportPolicies: vi.fn(), updateExportPolicies: vi.fn()
}))

import { listSources, listJobs, triggerCrawl, getCircuitBreakerStatus } from '../../src/api/imports.js'
import ImportDashboardView from '../../src/views/imports/ImportDashboardView.vue'
import { useAuthStore } from '../../src/stores/auth.js'

async function mountView({ role = 'SYSTEM_ADMIN' } = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  auth.user = { id: 'u1', roleType: role }
  return mount(ImportDashboardView, { global: { plugins: [pinia] } })
}

describe('ImportDashboardView', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('denies access for attendee users', async () => {
    const wrapper = await mountView({ role: 'ATTENDEE' })
    expect(wrapper.text()).toContain('Access Denied')
  })

  it('renders sources table', async () => {
    listSources.mockResolvedValue({ data: { data: [
      { id: 's1', name: 'Feed-A', folderPath: '/imports/a', active: true }
    ] } })
    listJobs.mockResolvedValue({ data: { data: { content: [] } } })
    getCircuitBreakerStatus.mockResolvedValue({ data: { data: {} } })
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('Feed-A')
    expect(wrapper.text()).toContain('/imports/a')
  })

  it('renders jobs table with status badges', async () => {
    listSources.mockResolvedValue({ data: { data: [{ id: 's1', name: 'A', folderPath: '/x', active: true }] } })
    listJobs.mockResolvedValue({ data: { data: { content: [
      { id: 'j1', sourceId: 's1', status: 'COMPLETED', filesProcessed: 5, recordsImported: 100, recordsFailed: 0 }
    ] } } })
    getCircuitBreakerStatus.mockResolvedValue({ data: { data: {} } })
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('COMPLETED')
    expect(wrapper.text()).toContain('100')
  })

  it('renders circuit breaker cards', async () => {
    listSources.mockResolvedValue({ data: { data: [{ id: 's1', name: 'A', folderPath: '/x', active: true }] } })
    listJobs.mockResolvedValue({ data: { data: { content: [] } } })
    getCircuitBreakerStatus.mockResolvedValue({ data: { data: { 's1': { status: 'TRIPPED', failureCount: 12 } } } })
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.find('.cb-grid').exists()).toBe(true)
    expect(wrapper.text()).toContain('TRIPPED')
    expect(wrapper.text()).toContain('Failures: 12')
  })

  it('Trigger Crawl opens modal and executes', async () => {
    listSources.mockResolvedValue({ data: { data: [{ id: 's1', name: 'A', folderPath: '/x', active: true }] } })
    listJobs.mockResolvedValue({ data: { data: { content: [] } } })
    getCircuitBreakerStatus.mockResolvedValue({ data: { data: {} } })
    triggerCrawl.mockResolvedValue({ data: { data: { jobId: 'new-job' } } })
    const wrapper = await mountView()
    await flushPromises()
    await wrapper.findAll('button').find(b => b.text() === 'Trigger Crawl').trigger('click')
    expect(wrapper.find('.modal').exists()).toBe(true)
    await wrapper.findAll('button').find(b => b.text() === 'Trigger').trigger('click')
    await flushPromises()
    expect(triggerCrawl).toHaveBeenCalledWith({ sourceId: 's1', mode: 'INCREMENTAL' })
  })

  it('Cancel button closes modal', async () => {
    listSources.mockResolvedValue({ data: { data: [{ id: 's1', name: 'A', folderPath: '/x', active: true }] } })
    listJobs.mockResolvedValue({ data: { data: { content: [] } } })
    getCircuitBreakerStatus.mockResolvedValue({ data: { data: {} } })
    const wrapper = await mountView()
    await flushPromises()
    await wrapper.findAll('button').find(b => b.text() === 'Trigger Crawl').trigger('click')
    expect(wrapper.find('.modal').exists()).toBe(true)
    await wrapper.findAll('button').find(b => b.text() === 'Cancel').trigger('click')
    expect(wrapper.find('.modal').exists()).toBe(false)
  })
})
