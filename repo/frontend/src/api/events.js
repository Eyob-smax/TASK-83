import apiClient from './client'

export function listEvents(params) {
  return apiClient.get('/events', { params })
}

export function getEvent(id) {
  return apiClient.get(`/events/${id}`)
}

export function getAvailability(id) {
  return apiClient.get(`/events/${id}/availability`)
}
