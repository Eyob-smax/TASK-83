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

import { listPeriods, createPeriod, executePosting, listRules } from '../../src/api/finance.js'

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
})
