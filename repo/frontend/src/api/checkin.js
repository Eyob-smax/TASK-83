import apiClient from './client'

export function getPasscode(sessionId) {
  return apiClient.get(`/checkin/sessions/${sessionId}/passcode`)
}

export function checkInAttendee(sessionId, data) {
  return apiClient.post(`/checkin/sessions/${sessionId}`, data)
}

export function getRoster(sessionId) {
  return apiClient.get(`/checkin/sessions/${sessionId}/roster`)
}

export function getConflicts(sessionId) {
  return apiClient.get(`/checkin/sessions/${sessionId}/conflicts`)
}
