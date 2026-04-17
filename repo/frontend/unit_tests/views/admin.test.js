import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

// --------------------------------------------------------------------------
// UserManagementView coverage
// --------------------------------------------------------------------------

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
  listSources: vi.fn(), createSource: vi.fn(), updateSource: vi.fn(),
  listJobs: vi.fn(), getJob: vi.fn(), triggerCrawl: vi.fn(), getCircuitBreakerStatus: vi.fn()
}))
vi.mock('../../src/api/exports.js', () => ({
  exportRoster: vi.fn(), exportFinanceReport: vi.fn(),
  downloadExport: vi.fn(), getExportPolicies: vi.fn(), updateExportPolicies: vi.fn()
}))

import { listUsers, createUser, updateUser } from '../../src/api/admin.js'
import UserManagementView from '../../src/views/admin/UserManagementView.vue'
import { useAuthStore } from '../../src/stores/auth.js'

async function mountUM({ role = 'SYSTEM_ADMIN' } = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  auth.user = { id: 'u1', roleType: role }
  return mount(UserManagementView, { global: { plugins: [pinia] } })
}

describe('UserManagementView', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('denies access for non-admin users', async () => {
    const wrapper = await mountUM({ role: 'ATTENDEE' })
    expect(wrapper.text()).toContain('Access Denied')
  })

  it('renders create-user form and users table after load', async () => {
    listUsers.mockResolvedValue({ data: { data: { content: [
      { id: 'u1', username: 'alice', displayName: 'Alice', roleType: 'ATTENDEE', status: 'ACTIVE' }
    ] } } })
    const wrapper = await mountUM()
    await flushPromises()
    expect(wrapper.find('#new-username').exists()).toBe(true)
    expect(wrapper.text()).toContain('alice')
    expect(wrapper.text()).toContain('ACTIVE')
  })

  it('create user form calls createUser with payload', async () => {
    listUsers.mockResolvedValue({ data: { data: { content: [] } } })
    createUser.mockResolvedValue({ data: { data: { id: 'u-new', username: 'new', roleType: 'ATTENDEE', status: 'ACTIVE' } } })
    const wrapper = await mountUM()
    await flushPromises()
    await wrapper.find('#new-username').setValue('new')
    await wrapper.find('#new-password').setValue('password123')
    await wrapper.find('#new-display').setValue('New User')
    await wrapper.find('#new-role').setValue('ATTENDEE')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    expect(createUser).toHaveBeenCalled()
    const payload = createUser.mock.calls[0][0]
    expect(payload.username).toBe('new')
    expect(payload.roleType).toBe('ATTENDEE')
  })

  it('disable button calls updateUser with DISABLED', async () => {
    listUsers.mockResolvedValue({ data: { data: { content: [
      { id: 'u1', username: 'alice', displayName: 'Alice', roleType: 'ATTENDEE', status: 'ACTIVE' }
    ] } } })
    updateUser.mockResolvedValue({ data: { data: { id: 'u1', status: 'DISABLED' } } })
    const wrapper = await mountUM()
    await flushPromises()
    await wrapper.findAll('button').find(b => b.text() === 'Disable').trigger('click')
    await flushPromises()
    expect(updateUser).toHaveBeenCalledWith('u1', { status: 'DISABLED' })
  })

  it('enable button shown for DISABLED users and calls updateUser with ACTIVE', async () => {
    listUsers.mockResolvedValue({ data: { data: { content: [
      { id: 'u1', username: 'alice', displayName: 'Alice', roleType: 'ATTENDEE', status: 'DISABLED' }
    ] } } })
    updateUser.mockResolvedValue({ data: { data: { id: 'u1', status: 'ACTIVE' } } })
    const wrapper = await mountUM()
    await flushPromises()
    const enableBtn = wrapper.findAll('button').find(b => b.text() === 'Enable')
    await enableBtn.trigger('click')
    await flushPromises()
    expect(updateUser).toHaveBeenCalledWith('u1', { status: 'ACTIVE' })
  })

  it('role change calls updateUser with new roleType', async () => {
    listUsers.mockResolvedValue({ data: { data: { content: [
      { id: 'u1', username: 'alice', displayName: 'Alice', roleType: 'ATTENDEE', status: 'ACTIVE' }
    ] } } })
    updateUser.mockResolvedValue({ data: { data: { id: 'u1', roleType: 'EVENT_STAFF' } } })
    const wrapper = await mountUM()
    await flushPromises()
    await wrapper.find('.inline-select').setValue('EVENT_STAFF')
    await flushPromises()
    expect(updateUser).toHaveBeenCalledWith('u1', { roleType: 'EVENT_STAFF' })
  })

  it('shows empty state when no users returned', async () => {
    listUsers.mockResolvedValue({ data: { data: { content: [] } } })
    const wrapper = await mountUM()
    await flushPromises()
    expect(wrapper.text()).toContain('No users found')
  })
})
