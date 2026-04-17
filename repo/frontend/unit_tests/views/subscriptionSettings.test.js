import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('../../src/api/notifications.js', () => ({
  listNotifications: vi.fn(),
  getUnreadCount: vi.fn(),
  markAsRead: vi.fn(),
  getSubscriptions: vi.fn(),
  updateSubscriptions: vi.fn(),
  getDndSettings: vi.fn(),
  updateDndSettings: vi.fn()
}))

import { getSubscriptions, updateSubscriptions, getDndSettings, updateDndSettings } from '../../src/api/notifications.js'
import SubscriptionSettingsView from '../../src/views/notifications/SubscriptionSettingsView.vue'

async function mountView() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/notifications', component: { template: '<div/>' } },
      { path: '/notifications/settings', component: SubscriptionSettingsView }
    ]
  })
  await router.push('/notifications/settings')
  await router.isReady()
  return mount(SubscriptionSettingsView, { global: { plugins: [pinia, router] } })
}

describe('SubscriptionSettingsView', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('shows loading spinner initially', async () => {
    getSubscriptions.mockImplementation(() => new Promise(() => {}))
    getDndSettings.mockImplementation(() => new Promise(() => {}))
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('Loading preferences')
  })

  it('renders toggle rows for all notification types', async () => {
    getSubscriptions.mockResolvedValue({ data: { data: [] } })
    getDndSettings.mockResolvedValue({ data: { data: null } })
    const wrapper = await mountView()
    await flushPromises()
    // There are 8 notification types in the view
    expect(wrapper.findAll('.toggle-row').length).toBeGreaterThanOrEqual(8)
  })

  it('toggle makes subsChanged true, enabling Save Subscriptions button', async () => {
    getSubscriptions.mockResolvedValue({ data: { data: [] } })
    getDndSettings.mockResolvedValue({ data: { data: null } })
    const wrapper = await mountView()
    await flushPromises()
    const saveBtn = wrapper.findAll('button').find(b => b.text().includes('Save Subscriptions'))
    expect(saveBtn.attributes('disabled')).toBeDefined()
    // Toggle first subscription
    await wrapper.findAll('input[type="checkbox"]')[0].trigger('change')
    await flushPromises()
    expect(saveBtn.attributes('disabled')).toBeUndefined()
  })

  it('save subscriptions calls updateSubscriptions and shows success', async () => {
    getSubscriptions.mockResolvedValue({ data: { data: [] } })
    getDndSettings.mockResolvedValue({ data: { data: null } })
    updateSubscriptions.mockResolvedValue({ data: { data: [] } })
    const wrapper = await mountView()
    await flushPromises()
    await wrapper.findAll('input[type="checkbox"]')[0].trigger('change')
    await flushPromises()
    await wrapper.findAll('button').find(b => b.text().includes('Save Subscriptions')).trigger('click')
    await flushPromises()
    expect(updateSubscriptions).toHaveBeenCalled()
    expect(wrapper.text()).toContain('Subscriptions saved')
  })

  it('DND toggle enables time inputs', async () => {
    getSubscriptions.mockResolvedValue({ data: { data: [] } })
    getDndSettings.mockResolvedValue({ data: { data: null } })
    const wrapper = await mountView()
    await flushPromises()
    // start time should be disabled initially
    expect(wrapper.find('#dnd-start').attributes('disabled')).toBeDefined()
    const toggles = wrapper.findAll('input[type="checkbox"]')
    const dndToggle = toggles[toggles.length - 1]
    await dndToggle.setValue(true)
    await flushPromises()
    expect(wrapper.find('#dnd-start').attributes('disabled')).toBeUndefined()
  })

  it('save DND calls updateDndSettings and shows success', async () => {
    getSubscriptions.mockResolvedValue({ data: { data: [] } })
    getDndSettings.mockResolvedValue({ data: { data: { enabled: false, startTime: '21:00', endTime: '07:00' } } })
    updateDndSettings.mockResolvedValue({ data: { data: { enabled: true, startTime: '22:00', endTime: '06:00' } } })
    const wrapper = await mountView()
    await flushPromises()
    const toggles = wrapper.findAll('input[type="checkbox"]')
    await toggles[toggles.length - 1].setValue(true)
    await flushPromises()
    await wrapper.findAll('button').find(b => b.text().includes('Save DND')).trigger('click')
    await flushPromises()
    expect(updateDndSettings).toHaveBeenCalled()
    expect(wrapper.text()).toContain('DND settings saved')
  })

  it('save subscription failure shows error', async () => {
    getSubscriptions.mockResolvedValue({ data: { data: [] } })
    getDndSettings.mockResolvedValue({ data: { data: null } })
    updateSubscriptions.mockRejectedValue({ response: { data: { message: 'save failed' } } })
    const wrapper = await mountView()
    await flushPromises()
    await wrapper.findAll('input[type="checkbox"]')[0].trigger('change')
    await flushPromises()
    await wrapper.findAll('button').find(b => b.text().includes('Save Subscriptions')).trigger('click')
    await flushPromises()
    // The store catches and sets notifStore.error, which renders the ErrorAlert
    expect(wrapper.text()).toContain('save failed')
  })

  it('initial unchanged state has both save buttons disabled', async () => {
    getSubscriptions.mockResolvedValue({ data: { data: [] } })
    getDndSettings.mockResolvedValue({ data: { data: null } })
    const wrapper = await mountView()
    await flushPromises()
    const saveButtons = wrapper.findAll('button').filter(b => b.text().includes('Save'))
    saveButtons.forEach(b => expect(b.attributes('disabled')).toBeDefined())
  })
})
