import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useEventsStore } from '../../src/stores/events.js'

vi.mock('../../src/api/events.js', () => ({
  listEvents: vi.fn(),
  getEvent: vi.fn(),
  getAvailability: vi.fn()
}))

import { listEvents, getEvent } from '../../src/api/events.js'

describe('events store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('initializes with empty events', () => {
    const store = useEventsStore()
    expect(store.events).toEqual([])
    expect(store.loading).toBe(false)
    expect(store.error).toBeNull()
  })

  it('fetchEvents sets loading state', async () => {
    listEvents.mockResolvedValue({
      data: { data: { content: [{ id: '1', title: 'Test' }], page: 0, totalPages: 1, totalElements: 1 } }
    })
    const store = useEventsStore()
    await store.fetchEvents()
    expect(store.events).toHaveLength(1)
    expect(store.loading).toBe(false)
  })

  it('fetchEvents sets error on failure', async () => {
    listEvents.mockRejectedValue({ response: { data: { message: 'Server error' } } })
    const store = useEventsStore()
    await store.fetchEvents()
    expect(store.error).toBe('Server error')
  })

  it('fetchEvent sets currentEvent', async () => {
    getEvent.mockResolvedValue({ data: { data: { id: '1', title: 'Session A', remainingSeats: 5 } } })
    const store = useEventsStore()
    await store.fetchEvent('1')
    expect(store.currentEvent.title).toBe('Session A')
  })
})
