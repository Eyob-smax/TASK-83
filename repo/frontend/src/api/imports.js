import apiClient from './client'

export function listSources(params) {
  return apiClient.get('/imports/sources', { params })
}

export function createSource(data) {
  return apiClient.post('/imports/sources', data)
}

export function updateSource(id, data) {
  return apiClient.put(`/imports/sources/${id}`, data)
}

export function listJobs(params) {
  return apiClient.get('/imports/jobs', { params })
}

export function getJob(id) {
  return apiClient.get(`/imports/jobs/${id}`)
}

export function triggerCrawl(data) {
  return apiClient.post('/imports/jobs/trigger', data)
}

export function getCircuitBreakerStatus() {
  return apiClient.get('/imports/circuit-breaker')
}
