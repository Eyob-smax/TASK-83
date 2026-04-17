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
  listSources,
  createSource,
  updateSource,
  listJobs,
  getJob,
  triggerCrawl,
  getCircuitBreakerStatus
} from '../../src/api/imports.js'

describe('api/imports', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('listSources forwards params', async () => {
    await listSources({ active: true })
    expect(apiClient.get).toHaveBeenCalledWith('/imports/sources', { params: { active: true } })
  })

  it('createSource posts body', async () => {
    const data = { name: 'Src1', url: 'https://x.example' }
    await createSource(data)
    expect(apiClient.post).toHaveBeenCalledWith('/imports/sources', data)
  })

  it('updateSource puts by id', async () => {
    const data = { active: false }
    await updateSource('s-1', data)
    expect(apiClient.put).toHaveBeenCalledWith('/imports/sources/s-1', data)
  })

  it('listJobs forwards params', async () => {
    await listJobs({ status: 'COMPLETED' })
    expect(apiClient.get).toHaveBeenCalledWith('/imports/jobs', { params: { status: 'COMPLETED' } })
  })

  it('getJob gets job by id', async () => {
    await getJob('job-1')
    expect(apiClient.get).toHaveBeenCalledWith('/imports/jobs/job-1')
  })

  it('triggerCrawl posts body', async () => {
    const data = { sourceId: 's-1' }
    await triggerCrawl(data)
    expect(apiClient.post).toHaveBeenCalledWith('/imports/jobs/trigger', data)
  })

  it('getCircuitBreakerStatus gets /imports/circuit-breaker', async () => {
    await getCircuitBreakerStatus()
    expect(apiClient.get).toHaveBeenCalledWith('/imports/circuit-breaker')
  })
})
