import apiClient from './client'

export function register(sessionId) {
  return apiClient.post('/registrations', { sessionId })
}

export function listRegistrations(params) {
  return apiClient.get('/registrations', { params })
}

export function getRegistration(id) {
  return apiClient.get(`/registrations/${id}`)
}

export function cancelRegistration(id) {
  return apiClient.delete(`/registrations/${id}`)
}

export function getWaitlistPositions() {
  return apiClient.get('/registrations/waitlist')
}
