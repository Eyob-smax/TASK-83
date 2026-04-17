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

import { listNotifications, getUnreadCount, markAsRead } from '../../src/api/notifications.js'
import NotificationCenterView from '../../src/views/notifications/NotificationCenterView.vue'

async function mountView() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: NotificationCenterView },
      { path: '/notifications/settings', component: { template: '<div/>' } }
    ]
  })
  await router.push('/')
  await router.isReady()
  return mount(NotificationCenterView, { global: { plugins: [pinia, router] } })
}

describe('NotificationCenterView', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('shows empty state when no notifications', async () => {
    listNotifications.mockResolvedValue({ data: { data: { content: [], page: 0, totalPages: 0, totalElements: 0 } } })
    getUnreadCount.mockResolvedValue({ data: { data: { count: 0 } } })
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('No notifications yet')
  })

  it('renders a notification list entry per item', async () => {
    listNotifications.mockResolvedValue({
      data: {
        data: {
          content: [
            { id: 'n1', type: 'REGISTRATION_CONFIRMED', subject: 'Registered', body: 'You are in', read: false, createdAt: new Date().toISOString() },
            { id: 'n2', type: 'SESSION_UPDATE', subject: 'Session change', body: 'Moved to Room B', read: true, createdAt: new Date().toISOString() }
          ],
          page: 0, totalPages: 1, totalElements: 2
        }
      }
    })
    getUnreadCount.mockResolvedValue({ data: { data: { count: 1 } } })
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.findAll('.notification-item')).toHaveLength(2)
    expect(wrapper.text()).toContain('Registered')
    expect(wrapper.text()).toContain('Session change')
  })

  it('shows unread badge count', async () => {
    listNotifications.mockResolvedValue({ data: { data: { content: [], page: 0, totalPages: 0, totalElements: 0 } } })
    getUnreadCount.mockResolvedValue({ data: { data: { count: 3 } } })
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.find('.unread-badge').text()).toBe('3')
  })

  it('clicking an unread notif calls markAsRead', async () => {
    listNotifications.mockResolvedValue({
      data: {
        data: {
          content: [
            { id: 'n1', type: 'INFO', subject: 'Hi', body: 'body', read: false, createdAt: new Date().toISOString() }
          ],
          page: 0, totalPages: 1, totalElements: 1
        }
      }
    })
    getUnreadCount.mockResolvedValue({ data: { data: { count: 1 } } })
    markAsRead.mockResolvedValue({})
    const wrapper = await mountView()
    await flushPromises()
    await wrapper.find('.notification-item').trigger('click')
    await flushPromises()
    expect(markAsRead).toHaveBeenCalledWith('n1')
  })

  it('clicking again collapses the body', async () => {
    listNotifications.mockResolvedValue({
      data: {
        data: {
          content: [
            { id: 'n1', type: 'INFO', subject: 'Hi', body: 'body here', read: true, createdAt: new Date().toISOString() }
          ],
          page: 0, totalPages: 1, totalElements: 1
        }
      }
    })
    getUnreadCount.mockResolvedValue({ data: { data: { count: 0 } } })
    const wrapper = await mountView()
    await flushPromises()
    await wrapper.find('.notification-item').trigger('click')
    expect(wrapper.find('.notification-body').exists()).toBe(true)
    await wrapper.find('.notification-item').trigger('click')
    expect(wrapper.find('.notification-body').exists()).toBe(false)
  })

  it('pagination shows when totalPages > 1', async () => {
    listNotifications.mockResolvedValue({
      data: {
        data: {
          content: [{ id: 'n1', type: 'INFO', subject: 'x', body: 'y', read: false, createdAt: new Date().toISOString() }],
          page: 0, totalPages: 3, totalElements: 30
        }
      }
    })
    getUnreadCount.mockResolvedValue({ data: { data: { count: 1 } } })
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.find('.pagination').exists()).toBe(true)
    expect(wrapper.text()).toContain('Page 1 of 3')
  })

  it('shows loading spinner while fetching', async () => {
    listNotifications.mockImplementation(() => new Promise(() => {}))
    getUnreadCount.mockResolvedValue({ data: { data: { count: 0 } } })
    const wrapper = await mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('Loading notifications')
  })
})
