import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('../../src/api/events.js', () => ({
  listEvents: vi.fn(),
  getEvent: vi.fn(),
  getAvailability: vi.fn()
}))

import { listEvents } from '../../src/api/events.js'
import EventListView from '../../src/views/events/EventListView.vue'
import { useEventsStore } from '../../src/stores/events.js'

async function mountList() {
  setActivePinia(createPinia())
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: EventListView },
      { path: '/events/:id', component: { template: '<div />' } }
    ]
  })
  await router.push('/')
  await router.isReady()
  const pinia = createPinia()
  setActivePinia(pinia)
  return { wrapper: mount(EventListView, { global: { plugins: [pinia, router] } }), router }
}

describe('EventListView', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('renders title "Event Sessions"', async () => {
    listEvents.mockResolvedValue({ data: { data: { content: [], page: 0, totalPages: 0, totalElements: 0 } } })
    const { wrapper } = await mountList()
    await flushPromises()
    expect(wrapper.find('h1').text()).toBe('Event Sessions')
  })

  it('shows empty state when no sessions', async () => {
    listEvents.mockResolvedValue({ data: { data: { content: [], page: 0, totalPages: 0, totalElements: 0 } } })
    const { wrapper } = await mountList()
    await flushPromises()
    expect(wrapper.text()).toContain('No sessions found')
  })

  it('shows loading spinner while loading', async () => {
    listEvents.mockImplementation(() => new Promise(() => {})) // never resolves
    const { wrapper } = await mountList()
    await flushPromises()
    expect(wrapper.text()).toContain('Loading sessions')
  })

  it('shows error alert with retry when fetch fails', async () => {
    listEvents.mockRejectedValue({ response: { data: { message: 'fetch failed' } } })
    const { wrapper } = await mountList()
    await flushPromises()
    expect(wrapper.text()).toContain('fetch failed')
    expect(wrapper.findAll('button').map(b => b.text())).toContain('Retry')
  })

  it('renders one card per event returned', async () => {
    listEvents.mockResolvedValue({
      data: {
        data: {
          content: [
            { id: 'e1', title: 'Morning Session', maxCapacity: 10, remainingSeats: 6, status: 'OPEN_FOR_REGISTRATION', startTime: '2026-05-01T10:00:00Z' },
            { id: 'e2', title: 'Evening Session', maxCapacity: 10, remainingSeats: 0, status: 'FULL', startTime: '2026-05-01T18:00:00Z' }
          ],
          page: 0,
          totalPages: 1,
          totalElements: 2
        }
      }
    })
    const { wrapper } = await mountList()
    await flushPromises()
    expect(wrapper.findAll('.event-card')).toHaveLength(2)
    expect(wrapper.text()).toContain('Morning Session')
    expect(wrapper.text()).toContain('Evening Session')
  })

  it('shows waitlist badge when remainingSeats is 0', async () => {
    listEvents.mockResolvedValue({
      data: {
        data: {
          content: [{ id: 'e1', title: 'X', maxCapacity: 10, remainingSeats: 0, status: 'FULL' }],
          page: 0, totalPages: 1, totalElements: 1
        }
      }
    })
    const { wrapper } = await mountList()
    await flushPromises()
    expect(wrapper.text()).toContain('Session Full')
  })

  it('calls fetchEvents with status filter on change', async () => {
    listEvents.mockResolvedValue({ data: { data: { content: [], page: 0, totalPages: 0, totalElements: 0 } } })
    const { wrapper } = await mountList()
    await flushPromises()
    listEvents.mockClear()
    await wrapper.find('select').setValue('OPEN_FOR_REGISTRATION')
    await flushPromises()
    expect(listEvents).toHaveBeenCalled()
    const params = listEvents.mock.calls[0][0]
    expect(params.status).toBe('OPEN_FOR_REGISTRATION')
  })

  it('pagination buttons disable appropriately', async () => {
    listEvents.mockResolvedValue({
      data: {
        data: {
          content: [{ id: 'e1', title: 'Solo', maxCapacity: 10, remainingSeats: 5, status: 'OPEN_FOR_REGISTRATION' }],
          page: 0, totalPages: 1, totalElements: 1
        }
      }
    })
    const { wrapper } = await mountList()
    await flushPromises()
    const [prev, next] = wrapper.findAll('.btn-page')
    expect(prev.attributes('disabled')).toBeDefined()
    expect(next.attributes('disabled')).toBeDefined()
  })
})
