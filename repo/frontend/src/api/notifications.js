import apiClient from './client'

export function listNotifications(params) {
  return apiClient.get('/notifications', { params })
}

export function getUnreadCount() {
  return apiClient.get('/notifications/unread-count')
}

export function markAsRead(id) {
  return apiClient.patch(`/notifications/${id}/read`)
}

export function getSubscriptions() {
  return apiClient.get('/notifications/subscriptions')
}

export function updateSubscriptions(data) {
  return apiClient.put('/notifications/subscriptions', data)
}

export function getDndSettings() {
  return apiClient.get('/notifications/dnd')
}

export function updateDndSettings(data) {
  return apiClient.put('/notifications/dnd', data)
}
