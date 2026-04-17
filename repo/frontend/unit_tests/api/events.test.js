import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../../src/api/client.js', () => ({
  default: {
    get: vi.fn(() => Promise.resolve({ data: {} })),
    post: vi.fn(() => Promise.resolve({ data: {} }))
  }
}))

import apiClient from '../../src/api/client.js'
import { listEvents, getEvent, getAvailability } from '../../src/api/events.js'

describe('api/events', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('listEvents forwards params to /events', async () => {
    await listEvents({ status: 'UPCOMING', page: 0 })
    expect(apiClient.get).toHaveBeenCalledWith('/events', { params: { status: 'UPCOMING', page: 0 } })
  })

  it('getEvent gets /events/{id}', async () => {
    await getEvent('ev-1')
    expect(apiClient.get).toHaveBeenCalledWith('/events/ev-1')
  })

  it('getAvailability gets /events/{id}/availability', async () => {
    await getAvailability('ev-2')
    expect(apiClient.get).toHaveBeenCalledWith('/events/ev-2/availability')
  })
})
