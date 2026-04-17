import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'

// --------------------------------------------------------------------------
// FinanceDashboardView component tests
// --------------------------------------------------------------------------

vi.mock('../../src/api/finance.js', () => ({
  listPeriods: vi.fn(),
  createPeriod: vi.fn(),
  listAccounts: vi.fn(),
  createAccount: vi.fn(),
  listCostCenters: vi.fn(),
  createCostCenter: vi.fn(),
  listRules: vi.fn(),
  createRule: vi.fn(),
  getRuleVersions: vi.fn(),
  executePosting: vi.fn(),
  listPostings: vi.fn(),
  getPostingLineItems: vi.fn()
}))

vi.mock('../../src/api/client.js', () => ({
  default: {
    put: vi.fn(() => Promise.resolve({ data: {} })),
    get: vi.fn(() => Promise.resolve({ data: {} })),
    post: vi.fn(() => Promise.resolve({ data: {} }))
  }
}))

import { listPeriods, listAccounts, listCostCenters, createPeriod } from '../../src/api/finance.js'
import FinanceDashboardView from '../../src/views/finance/FinanceDashboardView.vue'
import { useAuthStore } from '../../src/stores/auth.js'

async function mountDash({ role = 'FINANCE_MANAGER' } = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  auth.user = { id: 'u1', roleType: role }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: FinanceDashboardView },
      { path: '/finance/periods', component: { template: '<div/>' } },
      { path: '/finance/allocations', component: { template: '<div/>' } }
    ]
  })
  await router.push('/')
  await router.isReady()
  return mount(FinanceDashboardView, { global: { plugins: [pinia, router] } })
}

describe('FinanceDashboardView', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('denies access for attendee users', async () => {
    const wrapper = await mountDash({ role: 'ATTENDEE' })
    expect(wrapper.text()).toContain('Access Denied')
  })

  it('renders summary cards and table after load', async () => {
    listPeriods.mockResolvedValue({ data: { data: [
      { id: 'p1', name: 'Q1', startDate: '2026-01-01', endDate: '2026-03-31', status: 'OPEN' }
    ] } })
    listAccounts.mockResolvedValue({ data: { data: [] } })
    listCostCenters.mockResolvedValue({ data: { data: [{ id: 'c1', name: 'CC1', active: true }] } })
    const wrapper = await mountDash()
    await flushPromises()
    expect(wrapper.text()).toContain('Active Cost Centers')
    expect(wrapper.text()).toContain('Open Periods')
    expect(wrapper.text()).toContain('Q1')
    expect(wrapper.text()).toContain('OPEN')
  })

  it('shows empty state when no periods', async () => {
    listPeriods.mockResolvedValue({ data: { data: [] } })
    listAccounts.mockResolvedValue({ data: { data: [] } })
    listCostCenters.mockResolvedValue({ data: { data: [] } })
    const wrapper = await mountDash()
    await flushPromises()
    expect(wrapper.text()).toContain('No accounting periods found')
  })

  it('create period form submits to createPeriod', async () => {
    listPeriods.mockResolvedValue({ data: { data: [] } })
    listAccounts.mockResolvedValue({ data: { data: [] } })
    listCostCenters.mockResolvedValue({ data: { data: [] } })
    createPeriod.mockResolvedValue({ data: { data: { id: 'p-new', name: 'Q2', status: 'OPEN' } } })
    const wrapper = await mountDash()
    await flushPromises()
    await wrapper.find('#period-name').setValue('Q2')
    await wrapper.find('#period-start').setValue('2026-04-01')
    await wrapper.find('#period-end').setValue('2026-06-30')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    expect(createPeriod).toHaveBeenCalled()
    const payload = createPeriod.mock.calls[0][0]
    expect(payload.name).toBe('Q2')
  })
})
