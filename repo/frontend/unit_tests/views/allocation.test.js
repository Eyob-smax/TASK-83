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

import {
  listPeriods,
  listRules,
  listPostings,
  createRule,
  executePosting,
  getPostingLineItems
} from '../../src/api/finance.js'
import AllocationView from '../../src/views/finance/AllocationView.vue'
import { useAuthStore } from '../../src/stores/auth.js'

async function mountView({ role = 'FINANCE_MANAGER' } = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  auth.user = { id: 'u1', roleType: role }
  return mount(AllocationView, { global: { plugins: [pinia] } })
}

function mockBase() {
  listPeriods.mockResolvedValue({ data: { data: [{ id: 'p1', name: 'Q1', status: 'OPEN' }] } })
  listRules.mockResolvedValue({ data: { data: [{ id: 'r1', name: 'R1', allocationMethod: 'PROPORTIONAL', version: 1, active: true }] } })
  listPostings.mockResolvedValue({ data: { data: [] } })
}

describe('AllocationView', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('denies access for attendee users', async () => {
    const wrapper = await mountView({ role: 'ATTENDEE' })
    expect(wrapper.text()).toContain('Access Denied')
  })

  it('renders rules table and form when data loaded', async () => {
    mockBase()
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('R1')
    expect(wrapper.text()).toContain('PROPORTIONAL')
    expect(wrapper.find('#rule-method').exists()).toBe(true)
  })

  it('invalid JSON in rule config shows error without calling createRule', async () => {
    mockBase()
    const wrapper = await mountView()
    await flushPromises()
    await wrapper.find('#rule-name').setValue('BadRule')
    await wrapper.find('#rule-method').setValue('PROPORTIONAL')
    await wrapper.find('#rule-recognition').setValue('IMMEDIATE')
    await wrapper.find('#rule-config').setValue('{not-json}')
    await wrapper.findAll('form')[0].trigger('submit.prevent')
    await flushPromises()
    expect(createRule).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Invalid JSON')
  })

  it('valid JSON rule creation calls createRule', async () => {
    mockBase()
    createRule.mockResolvedValue({ data: { data: { id: 'r2', name: 'NewRule', allocationMethod: 'FIXED', version: 1, active: true } } })
    const wrapper = await mountView()
    await flushPromises()
    await wrapper.find('#rule-name').setValue('NewRule')
    await wrapper.find('#rule-method').setValue('FIXED')
    await wrapper.find('#rule-recognition').setValue('IMMEDIATE')
    await wrapper.find('#rule-config').setValue('{"splits":[{"id":"cc1","percentage":50}]}')
    await wrapper.findAll('form')[0].trigger('submit.prevent')
    await flushPromises()
    expect(createRule).toHaveBeenCalled()
    const payload = createRule.mock.calls[0][0]
    expect(payload.ruleConfig.splits).toHaveLength(1)
  })

  it('execute posting success shows posting result', async () => {
    mockBase()
    executePosting.mockResolvedValue({ data: { data: { id: 'post-1', totalAmount: 100, status: 'POSTED' } } })
    const wrapper = await mountView()
    await flushPromises()
    await wrapper.find('#post-period').setValue('p1')
    await wrapper.find('#post-rule').setValue('r1')
    await wrapper.find('#post-amount').setValue('100')
    await wrapper.findAll('form')[1].trigger('submit.prevent')
    await flushPromises()
    expect(executePosting).toHaveBeenCalled()
    expect(wrapper.text()).toContain('Posting Successful')
  })

  it('execute posting failure shows error', async () => {
    mockBase()
    executePosting.mockRejectedValue({ response: { data: { message: 'Period closed' } } })
    const wrapper = await mountView()
    await flushPromises()
    await wrapper.find('#post-period').setValue('p1')
    await wrapper.find('#post-rule').setValue('r1')
    await wrapper.find('#post-amount').setValue('100')
    await wrapper.findAll('form')[1].trigger('submit.prevent')
    await flushPromises()
    expect(wrapper.text()).toContain('Posting Failed')
  })

  it('line items panel opens when posting row clicked', async () => {
    listPeriods.mockResolvedValue({ data: { data: [{ id: 'p1', name: 'Q1', status: 'OPEN' }] } })
    listRules.mockResolvedValue({ data: { data: [{ id: 'r1', name: 'R1', allocationMethod: 'PROPORTIONAL', version: 1, active: true }] } })
    listPostings.mockResolvedValue({
      data: {
        data: [
          { id: 'post-1', periodId: 'p1', ruleId: 'r1', totalAmount: 500, status: 'POSTED' }
        ]
      }
    })
    getPostingLineItems.mockResolvedValue({ data: { data: [{ id: 'li-1', accountCode: '4000', amount: 500 }] } })
    const wrapper = await mountView()
    await flushPromises()
    const detailBtn = wrapper.findAll('button').find(b => b.text() === 'Line Items')
    await detailBtn.trigger('click')
    await flushPromises()
    expect(getPostingLineItems).toHaveBeenCalledWith('post-1')
    expect(wrapper.find('.line-items-panel').exists()).toBe(true)
  })
})
