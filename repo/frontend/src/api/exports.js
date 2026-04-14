import apiClient from './client'

export function exportRoster(data) {
  return apiClient.post('/exports/rosters', data)
}

export function exportFinanceReport(data) {
  return apiClient.post('/exports/finance-reports', data)
}

export function downloadExport(exportId) {
  return apiClient.get(`/exports/${exportId}/download`, { responseType: 'blob' })
}

export function getExportPolicies() {
  return apiClient.get('/exports/policies')
}

export function updateExportPolicies(data) {
  return apiClient.put('/exports/policies', data)
}
