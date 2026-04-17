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

import { getSecuritySettings } from '../../src/api/admin.js'
import SecuritySettingsView from '../../src/views/admin/SecuritySettingsView.vue'
import { useAuthStore } from '../../src/stores/auth.js'

async function mountView({ role = 'SYSTEM_ADMIN' } = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  auth.user = { id: 'u1', roleType: role }
  return mount(SecuritySettingsView, { global: { plugins: [pinia] } })
}

describe('SecuritySettingsView', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('denies access for non-admin users', async () => {
    const wrapper = await mountView({ role: 'EVENT_STAFF' })
    expect(wrapper.text()).toContain('Access Denied')
  })

  it('shows loading spinner before settings arrive', async () => {
    getSecuritySettings.mockImplementation(() => new Promise(() => {}))
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('Loading security settings')
  })

  it('renders 4 role rows in roles overview after init', async () => {
    getSecuritySettings.mockResolvedValue({ data: { data: { rateLimitPerMinute: 60, loginMaxAttempts: 5 } } })
    const wrapper = await mountView()
    await flushPromises()
    const table = wrapper.find('.data-table')
    expect(table.exists()).toBe(true)
    expect(table.findAll('tbody tr')).toHaveLength(4)
    expect(table.text()).toContain('ATTENDEE')
    expect(table.text()).toContain('EVENT_STAFF')
    expect(table.text()).toContain('FINANCE_MANAGER')
    expect(table.text()).toContain('SYSTEM_ADMIN')
  })

  it('renders rate limit configuration values', async () => {
    getSecuritySettings.mockResolvedValue({ data: { data: { rateLimitPerMinute: 100, loginMaxAttempts: 3, loginLockoutMinutes: 30 } } })
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('100 requests / minute')
    expect(wrapper.text()).toContain('3 attempts')
    expect(wrapper.text()).toContain('30 minutes')
  })

  it('renders signature and encryption status badges', async () => {
    getSecuritySettings.mockResolvedValue({ data: { data: { signatureMaxAgeSeconds: 300 } } })
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('HMAC-SHA256')
    expect(wrapper.text()).toContain('AES-256-GCM')
    expect(wrapper.text()).toContain('300 seconds')
  })
})
