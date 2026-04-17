import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../../src/api/client.js', () => ({
  default: {
    get: vi.fn(() => Promise.resolve({ data: {} })),
    post: vi.fn(() => Promise.resolve({ data: {} }))
  }
}))

import apiClient from '../../src/api/client.js'
import {
  listPeriods,
  createPeriod,
  listAccounts,
  createAccount,
  listCostCenters,
  createCostCenter,
  listRules,
  createRule,
  getRuleVersions,
  executePosting,
  listPostings,
  getPostingLineItems
} from '../../src/api/finance.js'

describe('api/finance', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('listPeriods forwards params', async () => {
    await listPeriods({ status: 'OPEN' })
    expect(apiClient.get).toHaveBeenCalledWith('/finance/periods', { params: { status: 'OPEN' } })
  })

  it('createPeriod posts body', async () => {
    const data = { name: 'Q1', startDate: '2026-01-01', endDate: '2026-03-31' }
    await createPeriod(data)
    expect(apiClient.post).toHaveBeenCalledWith('/finance/periods', data)
  })

  it('listAccounts forwards params', async () => {
    await listAccounts({ type: 'REVENUE' })
    expect(apiClient.get).toHaveBeenCalledWith('/finance/accounts', { params: { type: 'REVENUE' } })
  })

  it('createAccount posts body', async () => {
    const data = { code: '4000', name: 'Ticket revenue' }
    await createAccount(data)
    expect(apiClient.post).toHaveBeenCalledWith('/finance/accounts', data)
  })

  it('listCostCenters forwards params', async () => {
    await listCostCenters({ active: true })
    expect(apiClient.get).toHaveBeenCalledWith('/finance/cost-centers', { params: { active: true } })
  })

  it('createCostCenter posts body', async () => {
    const data = { code: 'CC-1', name: 'Events' }
    await createCostCenter(data)
    expect(apiClient.post).toHaveBeenCalledWith('/finance/cost-centers', data)
  })

  it('listRules forwards params', async () => {
    await listRules({ active: true })
    expect(apiClient.get).toHaveBeenCalledWith('/finance/rules', { params: { active: true } })
  })

  it('createRule posts body', async () => {
    const data = { name: 'R1', allocationMethod: 'PROPORTIONAL' }
    await createRule(data)
    expect(apiClient.post).toHaveBeenCalledWith('/finance/rules', data)
  })

  it('getRuleVersions gets rule history', async () => {
    await getRuleVersions('rule-1')
    expect(apiClient.get).toHaveBeenCalledWith('/finance/rules/rule-1/versions')
  })

  it('executePosting posts to /finance/postings', async () => {
    const data = { periodId: 'p1', ruleId: 'r1', totalAmount: 1000 }
    await executePosting(data)
    expect(apiClient.post).toHaveBeenCalledWith('/finance/postings', data)
  })

  it('listPostings forwards params', async () => {
    await listPostings({ periodId: 'p1' })
    expect(apiClient.get).toHaveBeenCalledWith('/finance/postings', { params: { periodId: 'p1' } })
  })

  it('getPostingLineItems gets line items', async () => {
    await getPostingLineItems('post-1')
    expect(apiClient.get).toHaveBeenCalledWith('/finance/postings/post-1/line-items')
  })
})
