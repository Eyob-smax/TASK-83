import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../../src/api/client.js', () => ({
  default: {
    get: vi.fn(() => Promise.resolve({ data: {} })),
    post: vi.fn(() => Promise.resolve({ data: {} })),
    put: vi.fn(() => Promise.resolve({ data: {} }))
  }
}))

import apiClient from '../../src/api/client.js'
import {
  exportRoster,
  exportFinanceReport,
  downloadExport,
  getExportPolicies,
  updateExportPolicies
} from '../../src/api/exports.js'

describe('api/exports', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('exportRoster posts body', async () => {
    const data = { eventId: 'e1' }
    await exportRoster(data)
    expect(apiClient.post).toHaveBeenCalledWith('/exports/rosters', data)
  })

  it('exportFinanceReport posts body', async () => {
    const data = { periodId: 'p1' }
    await exportFinanceReport(data)
    expect(apiClient.post).toHaveBeenCalledWith('/exports/finance-reports', data)
  })

  it('downloadExport requests blob response', async () => {
    await downloadExport('exp-1')
    expect(apiClient.get).toHaveBeenCalledWith('/exports/exp-1/download', { responseType: 'blob' })
  })

  it('getExportPolicies gets /exports/policies', async () => {
    await getExportPolicies()
    expect(apiClient.get).toHaveBeenCalledWith('/exports/policies')
  })

  it('updateExportPolicies puts body', async () => {
    const data = { reportType: 'ROSTER', downloadAllowed: false }
    await updateExportPolicies(data)
    expect(apiClient.put).toHaveBeenCalledWith('/exports/policies', data)
  })
})
