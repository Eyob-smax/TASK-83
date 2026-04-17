import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useEventsStore } from '../../src/stores/events.js'

vi.mock('../../src/api/events.js', () => ({
  listEvents: vi.fn(),
  getEvent: vi.fn(),
  getAvailability: vi.fn()
}))

import { listEvents, getEvent, getAvailability } from '../../src/api/events.js'

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

  it('fetchEvent sets error on failure', async () => {
    getEvent.mockRejectedValue({ response: { data: { message: 'not found' } } })
    const store = useEventsStore()
    await store.fetchEvent('x')
    expect(store.error).toBe('not found')
  })

  it('fetchAvailability updates currentEvent without affecting loading', async () => {
    getAvailability.mockResolvedValue({ data: { data: { id: '1', remainingSeats: 2 } } })
    const store = useEventsStore()
    await store.fetchAvailability('1')
    expect(store.currentEvent.remainingSeats).toBe(2)
  })

  it('fetchAvailability swallows errors silently', async () => {
    getAvailability.mockRejectedValue(new Error('boom'))
    const store = useEventsStore()
    await expect(store.fetchAvailability('1')).resolves.toBeUndefined()
  })
})
