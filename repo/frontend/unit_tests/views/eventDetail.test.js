import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('../../src/api/events.js', () => ({
  listEvents: vi.fn(),
  getEvent: vi.fn(),
  getAvailability: vi.fn()
}))

vi.mock('../../src/api/registration.js', () => ({
  register: vi.fn(),
  listRegistrations: vi.fn(),
  getRegistration: vi.fn(),
  cancelRegistration: vi.fn(),
  getWaitlistPositions: vi.fn()
}))

import { getEvent, getAvailability } from '../../src/api/events.js'
import { register } from '../../src/api/registration.js'
import EventDetailView from '../../src/views/events/EventDetailView.vue'

async function mountDetail(eventPayload) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/events/:id', component: EventDetailView }
    ]
  })
  await router.push('/events/e1')
  await router.isReady()
  const pinia = createPinia()
  setActivePinia(pinia)
  getEvent.mockResolvedValue({ data: { data: eventPayload } })
  getAvailability.mockResolvedValue({ data: { data: eventPayload } })
  const wrapper = mount(EventDetailView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return wrapper
}

describe('EventDetailView', () => {
  beforeEach(() => { vi.clearAllMocks(); vi.useFakeTimers() })
  afterEach(() => { vi.useRealTimers() })

  it('shows loading spinner initially', async () => {
    getEvent.mockImplementation(() => new Promise(() => {}))
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/events/:id', component: EventDetailView }]
    })
    await router.push('/events/e1')
    await router.isReady()
    const pinia = createPinia()
    const wrapper = mount(EventDetailView, { global: { plugins: [pinia, router] } })
    await flushPromises()
    expect(wrapper.text()).toContain('Loading session details')
  })

  it('renders event title and status for loaded event', async () => {
    const wrapper = await mountDetail({
      id: 'e1', title: 'Yoga', status: 'OPEN_FOR_REGISTRATION',
      maxCapacity: 10, remainingSeats: 6, startTime: '2026-05-01T10:00:00Z'
    })
    expect(wrapper.text()).toContain('Yoga')
    expect(wrapper.text()).toContain('OPEN FOR REGISTRATION')
  })

  it('register button shows "Register" for OPEN_FOR_REGISTRATION', async () => {
    const wrapper = await mountDetail({
      id: 'e1', title: 'X', status: 'OPEN_FOR_REGISTRATION', maxCapacity: 10, remainingSeats: 3
    })
    const btn = wrapper.find('.btn-register')
    expect(btn.text()).toContain('Register')
  })

  it('register button shows "Join Waitlist" when FULL', async () => {
    const wrapper = await mountDetail({
      id: 'e1', title: 'X', status: 'FULL', maxCapacity: 10, remainingSeats: 0
    })
    const btn = wrapper.find('.btn-register')
    expect(btn.text()).toContain('Join Waitlist')
  })

  it('register button shows disabled copy for non-registrable statuses', async () => {
    const wrapper = await mountDetail({
      id: 'e1', title: 'X', status: 'COMPLETED', maxCapacity: 10, remainingSeats: 10
    })
    const btn = wrapper.find('.btn-register')
    expect(btn.text()).toContain('Registration Not Open')
    expect(btn.attributes('disabled')).toBeDefined()
  })

  it('successful registration shows CONFIRMED status message', async () => {
    const wrapper = await mountDetail({
      id: 'e1', title: 'X', status: 'OPEN_FOR_REGISTRATION', maxCapacity: 10, remainingSeats: 3
    })
    register.mockResolvedValue({ data: { data: { id: 'r1', status: 'CONFIRMED' } } })
    await wrapper.find('.btn-register').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Registration Successful')
    expect(wrapper.text()).toContain('CONFIRMED')
  })

  it('displays device binding notice when required', async () => {
    const wrapper = await mountDetail({
      id: 'e1', title: 'X', status: 'OPEN_FOR_REGISTRATION',
      maxCapacity: 10, remainingSeats: 5, deviceBindingRequired: true
    })
    expect(wrapper.text()).toContain('Device binding')
  })

  it('auto-refresh calls fetchAvailability after 10 seconds', async () => {
    await mountDetail({
      id: 'e1', title: 'X', status: 'OPEN_FOR_REGISTRATION', maxCapacity: 10, remainingSeats: 5
    })
    getAvailability.mockClear()
    vi.advanceTimersByTime(10001)
    expect(getAvailability).toHaveBeenCalledWith('e1')
  })
})
