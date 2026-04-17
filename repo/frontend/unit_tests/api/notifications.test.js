import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('../../src/api/client.js', () => ({
  default: {
    get: vi.fn(() => Promise.resolve({ data: {} })),
    post: vi.fn(() => Promise.resolve({ data: {} })),
    put: vi.fn(() => Promise.resolve({ data: {} })),
    patch: vi.fn(() => Promise.resolve({ data: {} }))
  }
}))

import apiClient from '../../src/api/client.js'
import {
  listNotifications,
  getUnreadCount,
  markAsRead,
  getSubscriptions,
  updateSubscriptions,
  getDndSettings,
  updateDndSettings
} from '../../src/api/notifications.js'

describe('api/notifications', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('listNotifications forwards params', async () => {
    await listNotifications({ unread: true })
    expect(apiClient.get).toHaveBeenCalledWith('/notifications', { params: { unread: true } })
  })

  it('getUnreadCount gets /notifications/unread-count', async () => {
    await getUnreadCount()
    expect(apiClient.get).toHaveBeenCalledWith('/notifications/unread-count')
  })

  it('markAsRead patches /notifications/{id}/read', async () => {
    await markAsRead('n-1')
    expect(apiClient.patch).toHaveBeenCalledWith('/notifications/n-1/read')
  })

  it('getSubscriptions gets /notifications/subscriptions', async () => {
    await getSubscriptions()
    expect(apiClient.get).toHaveBeenCalledWith('/notifications/subscriptions')
  })

  it('updateSubscriptions puts body', async () => {
    const data = { items: [{ type: 'EVENT_UPDATES', enabled: false }] }
    await updateSubscriptions(data)
    expect(apiClient.put).toHaveBeenCalledWith('/notifications/subscriptions', data)
  })

  it('getDndSettings gets /notifications/dnd', async () => {
    await getDndSettings()
    expect(apiClient.get).toHaveBeenCalledWith('/notifications/dnd')
  })

  it('updateDndSettings puts body', async () => {
    const data = { enabled: true, startTime: '22:00', endTime: '06:00' }
    await updateDndSettings(data)
    expect(apiClient.put).toHaveBeenCalledWith('/notifications/dnd', data)
  })
})
