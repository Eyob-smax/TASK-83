import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useNotificationsStore } from '../../src/stores/notifications.js'

vi.mock('../../src/api/notifications.js', () => ({
  listNotifications: vi.fn(),
  getUnreadCount: vi.fn(),
  markAsRead: vi.fn(),
  getSubscriptions: vi.fn(),
  updateSubscriptions: vi.fn(),
  getDndSettings: vi.fn(),
  updateDndSettings: vi.fn()
}))

import * as notifApi from '../../src/api/notifications.js'

describe('notifications store', () => {
  beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks() })

  it('initializes with empty notifications', () => {
    const store = useNotificationsStore()
    expect(store.notifications).toEqual([])
    expect(store.unreadCount).toBe(0)
  })

  it('fetchUnreadCount updates count', async () => {
    notifApi.getUnreadCount.mockResolvedValue({ data: { data: { count: 5 } } })
    const store = useNotificationsStore()
    await store.fetchUnreadCount()
    expect(store.unreadCount).toBe(5)
  })

  it('markAsRead decrements unread count', async () => {
    notifApi.markAsRead.mockResolvedValue({})
    const store = useNotificationsStore()
    store.unreadCount = 3
    store.notifications = [{ id: 'n1', read: false }]
    await store.markAsRead('n1')
    expect(store.unreadCount).toBe(2)
    expect(store.notifications[0].read).toBe(true)
  })

  it('fetchNotifications populates paginated list', async () => {
    notifApi.listNotifications.mockResolvedValue({
      data: { data: { content: [{ id: 'n1', subject: 'Test', read: false }], page: 0, totalPages: 1, totalElements: 1 } }
    })
    const store = useNotificationsStore()
    await store.fetchNotifications()
    expect(store.notifications).toHaveLength(1)
  })

  it('fetchDndSettings populates dnd', async () => {
    notifApi.getDndSettings.mockResolvedValue({ data: { data: { startTime: '21:00', endTime: '07:00', enabled: true } } })
    const store = useNotificationsStore()
    await store.fetchDndSettings()
    expect(store.dndSettings.startTime).toBe('21:00')
  })

  it('fetchNotifications sets error on failure', async () => {
    notifApi.listNotifications.mockRejectedValue({ response: { data: { message: 'boom' } } })
    const store = useNotificationsStore()
    await store.fetchNotifications()
    expect(store.error).toBe('boom')
  })

  it('fetchSubscriptions populates subscriptions', async () => {
    notifApi.getSubscriptions.mockResolvedValue({ data: { data: [{ type: 'A', enabled: true }] } })
    const store = useNotificationsStore()
    await store.fetchSubscriptions()
    expect(store.subscriptions).toHaveLength(1)
  })

  it('updateSubscriptions updates state on success', async () => {
    notifApi.updateSubscriptions.mockResolvedValue({})
    const store = useNotificationsStore()
    await store.updateSubscriptions([{ type: 'A', enabled: false }])
    expect(store.subscriptions).toHaveLength(1)
  })

  it('updateDnd updates state on success', async () => {
    notifApi.updateDndSettings.mockResolvedValue({ data: { data: { enabled: true, startTime: '22:00', endTime: '06:00' } } })
    const store = useNotificationsStore()
    await store.updateDnd({ enabled: true, startTime: '22:00', endTime: '06:00' })
    expect(store.dndSettings.startTime).toBe('22:00')
  })

  it('markAsRead does not decrement past zero', async () => {
    notifApi.markAsRead.mockResolvedValue({})
    const store = useNotificationsStore()
    store.unreadCount = 0
    store.notifications = [{ id: 'n1', read: false }]
    await store.markAsRead('n1')
    expect(store.unreadCount).toBe(0)
  })

  it('markAsRead sets error on API failure', async () => {
    notifApi.markAsRead.mockRejectedValue({ response: { data: { message: 'fail' } } })
    const store = useNotificationsStore()
    await store.markAsRead('n1')
    expect(store.error).toBe('fail')
  })
})
