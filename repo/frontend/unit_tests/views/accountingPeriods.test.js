import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

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

import { listPeriods, listAccounts, listCostCenters, createAccount, createCostCenter } from '../../src/api/finance.js'
import AccountingPeriodsView from '../../src/views/finance/AccountingPeriodsView.vue'
import { useAuthStore } from '../../src/stores/auth.js'

async function mountView({ role = 'FINANCE_MANAGER' } = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  auth.user = { id: 'u1', roleType: role }
  return mount(AccountingPeriodsView, { global: { plugins: [pinia] } })
}

function mockAllSuccess() {
  listPeriods.mockResolvedValue({ data: { data: [{ id: 'p1', name: 'Q1', startDate: '2026-01-01', endDate: '2026-03-31', status: 'OPEN' }] } })
  listAccounts.mockResolvedValue({ data: { data: [{ id: 'a1', code: '4000', name: 'Revenue', type: 'REVENUE', active: true }] } })
  listCostCenters.mockResolvedValue({ data: { data: [{ id: 'c1', code: 'CC1', name: 'Marketing', type: 'DEPARTMENT', active: true }] } })
}

describe('AccountingPeriodsView', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('denies access for attendee users', async () => {
    const wrapper = await mountView({ role: 'ATTENDEE' })
    expect(wrapper.text()).toContain('Access Denied')
  })

  it('renders periods rows', async () => {
    mockAllSuccess()
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('Q1')
    expect(wrapper.text()).toContain('OPEN')
  })

  it('renders accounts and cost centers rows', async () => {
    mockAllSuccess()
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('4000')
    expect(wrapper.text()).toContain('Revenue')
    expect(wrapper.text()).toContain('CC1')
    expect(wrapper.text()).toContain('Marketing')
  })

  it('shows Close button only for OPEN periods', async () => {
    listPeriods.mockResolvedValue({
      data: {
        data: [
          { id: 'p1', name: 'Q1', startDate: '2026-01-01', endDate: '2026-03-31', status: 'OPEN' },
          { id: 'p2', name: 'Q0', startDate: '2025-10-01', endDate: '2025-12-31', status: 'CLOSED' }
        ]
      }
    })
    listAccounts.mockResolvedValue({ data: { data: [] } })
    listCostCenters.mockResolvedValue({ data: { data: [] } })
    const wrapper = await mountView()
    await flushPromises()
    const closeButtons = wrapper.findAll('.btn-close-period')
    expect(closeButtons).toHaveLength(1)
  })

  it('create account form submits to createAccount', async () => {
    mockAllSuccess()
    createAccount.mockResolvedValue({ data: { data: { id: 'a2', code: '5000', name: 'COGS', type: 'EXPENSE', active: true } } })
    const wrapper = await mountView()
    await flushPromises()
    await wrapper.find('#acct-code').setValue('5000')
    await wrapper.find('#acct-name').setValue('COGS')
    await wrapper.find('#acct-type').setValue('EXPENSE')
    await wrapper.findAll('form')[0].trigger('submit.prevent')
    await flushPromises()
    expect(createAccount).toHaveBeenCalled()
    const payload = createAccount.mock.calls[0][0]
    expect(payload.code).toBe('5000')
    expect(payload.type).toBe('EXPENSE')
  })

  it('create cost center form submits to createCostCenter', async () => {
    mockAllSuccess()
    createCostCenter.mockResolvedValue({ data: { data: { id: 'c2', code: 'CC2', name: 'Sales', type: 'DEPARTMENT', active: true } } })
    const wrapper = await mountView()
    await flushPromises()
    await wrapper.find('#cc-code').setValue('CC2')
    await wrapper.find('#cc-name').setValue('Sales')
    await wrapper.find('#cc-type').setValue('DEPARTMENT')
    await wrapper.findAll('form')[1].trigger('submit.prevent')
    await flushPromises()
    expect(createCostCenter).toHaveBeenCalled()
  })
})
