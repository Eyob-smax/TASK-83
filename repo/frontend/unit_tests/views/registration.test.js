import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'

// --------------------------------------------------------------------------
// RegistrationView + WaitlistView tests
// --------------------------------------------------------------------------

vi.mock('../../src/api/registration.js', () => ({
  register: vi.fn(),
  listRegistrations: vi.fn(),
  getRegistration: vi.fn(),
  cancelRegistration: vi.fn(),
  getWaitlistPositions: vi.fn()
}))

import { listRegistrations, cancelRegistration, getWaitlistPositions } from '../../src/api/registration.js'
import RegistrationView from '../../src/views/registration/RegistrationView.vue'
import WaitlistView from '../../src/views/registration/WaitlistView.vue'

async function mountRegView() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div/>' } },
      { path: '/events/:id', component: { template: '<div/>' } },
      { path: '/registrations', component: RegistrationView }
    ]
  })
  await router.push('/registrations')
  await router.isReady()
  return mount(RegistrationView, { global: { plugins: [pinia, router] } })
}

async function mountWaitlistView() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div/>' } },
      { path: '/events/:id', component: { template: '<div/>' } },
      { path: '/waitlist', component: WaitlistView }
    ]
  })
  await router.push('/waitlist')
  await router.isReady()
  return mount(WaitlistView, { global: { plugins: [pinia, router] } })
}

describe('RegistrationView', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('shows empty state when no registrations', async () => {
    listRegistrations.mockResolvedValue({ data: { data: [] } })
    const wrapper = await mountRegView()
    await flushPromises()
    expect(wrapper.text()).toContain('No registrations yet')
  })

  it('renders a card per registration', async () => {
    listRegistrations.mockResolvedValue({
      data: { data: [
        { id: 'r1', sessionId: 's1', sessionTitle: 'Morning', status: 'CONFIRMED' },
        { id: 'r2', sessionId: 's2', sessionTitle: 'Evening', status: 'WAITLISTED' }
      ] }
    })
    const wrapper = await mountRegView()
    await flushPromises()
    expect(wrapper.findAll('.reg-card')).toHaveLength(2)
    expect(wrapper.text()).toContain('Morning')
    expect(wrapper.text()).toContain('CONFIRMED')
  })

  it('cancel button triggers confirm dialog', async () => {
    listRegistrations.mockResolvedValue({
      data: { data: [
        { id: 'r1', sessionId: 's1', sessionTitle: 'Morning', status: 'CONFIRMED' }
      ] }
    })
    const wrapper = await mountRegView()
    await flushPromises()
    await wrapper.findAll('button').find(b => b.text().includes('Cancel Registration')).trigger('click')
    await flushPromises()
    // ConfirmDialog shows; click its Confirm button
    const confirmBtn = wrapper.findAll('button').find(b => b.text() === 'Confirm')
    expect(confirmBtn).toBeDefined()
    cancelRegistration.mockResolvedValue({})
    await confirmBtn.trigger('click')
    await flushPromises()
    expect(cancelRegistration).toHaveBeenCalledWith('r1')
  })

  it('does not show Cancel button for CANCELLED registrations', async () => {
    listRegistrations.mockResolvedValue({
      data: { data: [
        { id: 'r1', sessionId: 's1', sessionTitle: 'Morning', status: 'CANCELLED' }
      ] }
    })
    const wrapper = await mountRegView()
    await flushPromises()
    expect(wrapper.findAll('button').map(b => b.text()).some(t => t.includes('Cancel Registration'))).toBe(false)
  })
})

describe('WaitlistView', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('shows empty state when no waitlist entries', async () => {
    getWaitlistPositions.mockResolvedValue({ data: { data: [] } })
    const wrapper = await mountWaitlistView()
    await flushPromises()
    expect(wrapper.text()).toContain('no waitlist entries')
  })

  it('renders a waitlist card per entry with position number', async () => {
    getWaitlistPositions.mockResolvedValue({
      data: { data: [
        { id: 'w1', sessionId: 's1', sessionTitle: 'Yoga', waitlistPosition: 2 }
      ] }
    })
    const wrapper = await mountWaitlistView()
    await flushPromises()
    expect(wrapper.find('.waitlist-card').exists()).toBe(true)
    expect(wrapper.text()).toContain('Yoga')
    expect(wrapper.text()).toContain('2')
  })

  it('shows WAITLISTED badge on each entry', async () => {
    getWaitlistPositions.mockResolvedValue({
      data: { data: [
        { id: 'w1', sessionId: 's1', sessionTitle: 'A', waitlistPosition: 1 }
      ] }
    })
    const wrapper = await mountWaitlistView()
    await flushPromises()
    expect(wrapper.text()).toContain('WAITLISTED')
  })

  it('shows loading spinner before data loads', async () => {
    getWaitlistPositions.mockImplementation(() => new Promise(() => {}))
    const wrapper = await mountWaitlistView()
    await flushPromises()
    expect(wrapper.text()).toContain('Loading waitlist')
  })
})
