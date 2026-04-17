import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useFinanceStore } from '../../src/stores/finance.js'

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
  listPeriods, createPeriod, executePosting, listRules, createRule,
  listAccounts, createAccount, listCostCenters, createCostCenter,
  listPostings, getPostingLineItems
} from '../../src/api/finance.js'

describe('finance store', () => {
  beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks() })

  it('initializes with empty arrays', () => {
    const store = useFinanceStore()
    expect(store.periods).toEqual([])
    expect(store.rules).toEqual([])
    expect(store.postings).toEqual([])
  })

  it('fetchPeriods populates periods', async () => {
    listPeriods.mockResolvedValue({ data: { data: [{ id: 'p1', name: 'Q1', status: 'OPEN' }] } })
    const store = useFinanceStore()
    await store.fetchPeriods()
    expect(store.periods).toHaveLength(1)
    expect(store.periods[0].status).toBe('OPEN')
  })

  it('createPeriod adds to list and sets success', async () => {
    createPeriod.mockResolvedValue({ data: { data: { id: 'p2', name: 'Q2', status: 'OPEN' } } })
    const store = useFinanceStore()
    await store.createPeriod({ name: 'Q2', startDate: '2026-04-01', endDate: '2026-06-30' })
    expect(store.periods).toHaveLength(1)
    expect(store.successMessage).toBe('Period created')
  })

  it('executePosting handles PERIOD_CLOSED error', async () => {
    executePosting.mockRejectedValue({ response: { data: { message: 'Period is closed' } } })
    const store = useFinanceStore()
    await expect(store.executePosting({ periodId: 'p1', ruleId: 'r1', totalAmount: 1000 })).rejects.toThrow()
    expect(store.error).toBe('Period is closed')
  })

  it('fetchRules populates with version info', async () => {
    listRules.mockResolvedValue({ data: { data: [{ id: 'r1', name: 'Rule A', version: 3, active: true }] } })
    const store = useFinanceStore()
    await store.fetchRules()
    expect(store.rules[0].version).toBe(3)
  })

  it('fetchAccounts populates accounts and sets error on failure', async () => {
    listAccounts.mockResolvedValue({ data: { data: [{ id: 'a1', code: '4000', name: 'Rev' }] } })
    const store = useFinanceStore()
    await store.fetchAccounts()
    expect(store.accounts).toHaveLength(1)
    listAccounts.mockRejectedValue({ response: { data: { message: 'err' } } })
    await store.fetchAccounts()
    expect(store.error).toBe('err')
  })

  it('createAccount appends account and sets success', async () => {
    createAccount.mockResolvedValue({ data: { data: { id: 'a2', code: '5000' } } })
    const store = useFinanceStore()
    await store.createAccount({ code: '5000', name: 'X', type: 'ASSET' })
    expect(store.accounts).toHaveLength(1)
    expect(store.successMessage).toBe('Account created')
  })

  it('fetchCostCenters populates and createCostCenter appends', async () => {
    listCostCenters.mockResolvedValue({ data: { data: [{ id: 'c1', code: 'CC1' }] } })
    createCostCenter.mockResolvedValue({ data: { data: { id: 'c2', code: 'CC2' } } })
    const store = useFinanceStore()
    await store.fetchCostCenters()
    expect(store.costCenters).toHaveLength(1)
    await store.createCostCenter({ code: 'CC2', name: 'X', type: 'DEPARTMENT' })
    expect(store.costCenters).toHaveLength(2)
    expect(store.successMessage).toBe('Cost center created')
  })

  it('createRule appends rule and sets success', async () => {
    createRule.mockResolvedValue({ data: { data: { id: 'r-new', name: 'Rule' } } })
    const store = useFinanceStore()
    await store.createRule({ name: 'Rule', allocationMethod: 'FIXED', recognitionMethod: 'IMMEDIATE' })
    expect(store.rules).toHaveLength(1)
    expect(store.successMessage).toContain('Rule created')
  })

  it('fetchPostings populates postings', async () => {
    listPostings.mockResolvedValue({ data: { data: [{ id: 'post-1' }] } })
    const store = useFinanceStore()
    await store.fetchPostings('p1')
    expect(store.postings).toHaveLength(1)
  })

  it('fetchLineItems populates currentLineItems', async () => {
    getPostingLineItems.mockResolvedValue({ data: { data: [{ id: 'li1' }] } })
    const store = useFinanceStore()
    await store.fetchLineItems('post-1')
    expect(store.currentLineItems).toHaveLength(1)
  })

  it('executePosting success appends posting', async () => {
    executePosting.mockResolvedValue({ data: { data: { id: 'post-new', status: 'POSTED' } } })
    const store = useFinanceStore()
    await store.executePosting({ periodId: 'p1', ruleId: 'r1', totalAmount: 100 })
    expect(store.postings).toHaveLength(1)
  })

  it('clearMessages resets error and success', () => {
    const store = useFinanceStore()
    store.error = 'x'
    store.successMessage = 'y'
    store.clearMessages()
    expect(store.error).toBeNull()
    expect(store.successMessage).toBeNull()
  })

  it('updateRule creates new version and marks old inactive', async () => {
    createRule.mockResolvedValue({ data: { data: { id: 'r-v2', version: 2 } } })
    const store = useFinanceStore()
    store.rules = [{ id: 'r1', version: 1, active: true }]
    await store.updateRule('r1', { name: 'X' })
    const oldRule = store.rules.find(r => r.id === 'r1')
    expect(oldRule.active).toBe(false)
    expect(store.rules).toHaveLength(2)
  })
})
