import apiClient from './client'

export function listUsers(params) {
  return apiClient.get('/admin/users', { params })
}

export function createUser(data) {
  return apiClient.post('/admin/users', data)
}

export function updateUser(id, data) {
  return apiClient.put(`/admin/users/${id}`, data)
}

export function getSecuritySettings() {
  return apiClient.get('/admin/security/settings')
}

export function updateSecuritySettings(data) {
  return apiClient.put('/admin/security/settings', data)
}

export function listAuditLogs(params) {
  return apiClient.get('/audit/logs', { params })
}

export function getAuditLog(id) {
  return apiClient.get(`/audit/logs/${id}`)
}

export function exportAuditLogs(params) {
  return apiClient.post('/audit/logs/export', params)
}

export function downloadAuditExport(exportId) {
  return apiClient.get(`/audit/exports/${exportId}/download`, { responseType: 'blob' })
}

export function listBackups(params) {
  return apiClient.get('/admin/backups', { params })
}

export function getBackup(id) {
  return apiClient.get(`/admin/backups/${id}`)
}

export function triggerBackup() {
  return apiClient.post('/admin/backups/trigger')
}

export function getRetentionStatus() {
  return apiClient.get('/admin/backups/retention')
}
