import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as adminApi from '@/api/admin'
import * as importApi from '@/api/imports'
import * as exportApi from '@/api/exports'

export const useAdminStore = defineStore('admin', () => {
  const users = ref([])
  const auditLogs = ref([])
  const auditPagination = ref({ page: 0, totalPages: 0, totalElements: 0 })
  const currentAuditLog = ref(null)
  const backups = ref([])
  const retentionStatus = ref(null)
  const securitySettings = ref(null)
  const importSources = ref([])
  const importJobs = ref([])
  const circuitBreakerStatus = ref(null)
  const exportPolicies = ref([])
  const loading = ref(false)
  const error = ref(null)
  const successMessage = ref(null)

  async function fetchUsers(params) {
    loading.value = true; error.value = null
    try {
      const res = await adminApi.listUsers(params)
      users.value = res.data.data?.content || res.data.data
    } catch (e) { error.value = e.response?.data?.message || 'Failed to load users' }
    finally { loading.value = false }
  }
  async function createUser(data) {
    loading.value = true; error.value = null
    try { const res = await adminApi.createUser(data); users.value.push(res.data.data); successMessage.value = 'User created' }
    catch (e) { error.value = e.response?.data?.message || 'Failed to create user' }
    finally { loading.value = false }
  }
  async function updateUser(id, data) {
    loading.value = true; error.value = null
    try { const res = await adminApi.updateUser(id, data); const idx = users.value.findIndex(u => u.id === id); if (idx >= 0) users.value[idx] = res.data.data; successMessage.value = 'User updated' }
    catch (e) { error.value = e.response?.data?.message || 'Failed to update user' }
    finally { loading.value = false }
  }

  async function fetchAuditLogs(params) {
    loading.value = true; error.value = null
    try {
      const res = await adminApi.listAuditLogs(params)
      const page = res.data.data
      auditLogs.value = page.content
      auditPagination.value = { page: page.page, totalPages: page.totalPages, totalElements: page.totalElements }
    } catch (e) { error.value = e.response?.data?.message || 'Failed to load audit logs' }
    finally { loading.value = false }
  }
  async function fetchAuditLog(id) {
    loading.value = true; error.value = null
    try { const res = await adminApi.getAuditLog(id); currentAuditLog.value = res.data.data }
    catch (e) { error.value = e.response?.data?.message || 'Failed to load audit log' }
    finally { loading.value = false }
  }
  async function triggerAuditExport(params) {
    loading.value = true; error.value = null
    try { const res = await adminApi.exportAuditLogs(params); successMessage.value = 'Export queued'; return res.data.data }
    catch (e) { error.value = e.response?.data?.message || 'Export failed'; throw e }
    finally { loading.value = false }
  }

  async function fetchBackups() {
    loading.value = true; error.value = null
    try { const res = await adminApi.listBackups(); backups.value = res.data.data }
    catch (e) { error.value = e.response?.data?.message || 'Failed to load backups' }
    finally { loading.value = false }
  }
  async function triggerBackup() {
    loading.value = true; error.value = null
    try { const res = await adminApi.triggerBackup(); backups.value.unshift(res.data.data); successMessage.value = 'Backup triggered' }
    catch (e) { error.value = e.response?.data?.message || 'Backup failed' }
    finally { loading.value = false }
  }
  async function fetchRetentionStatus() {
    try { const res = await adminApi.getRetentionStatus(); retentionStatus.value = res.data.data }
    catch (e) { /* silent */ }
  }

  async function fetchSecuritySettings() {
    loading.value = true; error.value = null
    try { const res = await adminApi.getSecuritySettings(); securitySettings.value = res.data.data }
    catch (e) { error.value = e.response?.data?.message || 'Failed to load settings' }
    finally { loading.value = false }
  }
  async function updateSecuritySettings(data) {
    loading.value = true; error.value = null
    try {
      const res = await adminApi.updateSecuritySettings(data)
      securitySettings.value = res.data.data
      successMessage.value = 'Security settings updated'
    } catch (e) { error.value = e.response?.data?.message || 'Failed to update settings' }
    finally { loading.value = false }
  }

  async function fetchImportSources() {
    loading.value = true; error.value = null
    try { const res = await importApi.listSources(); importSources.value = res.data.data }
    catch (e) { error.value = e.response?.data?.message || 'Failed to load sources' }
    finally { loading.value = false }
  }
  async function fetchImportJobs(params) {
    loading.value = true; error.value = null
    try { const res = await importApi.listJobs(params); importJobs.value = res.data.data?.content || res.data.data }
    catch (e) { error.value = e.response?.data?.message || 'Failed to load jobs' }
    finally { loading.value = false }
  }
  async function triggerImportCrawl(data) {
    loading.value = true; error.value = null
    try { const res = await importApi.triggerCrawl(data); successMessage.value = 'Crawl triggered'; return res.data.data }
    catch (e) { error.value = e.response?.data?.message || 'Crawl trigger failed'; throw e }
    finally { loading.value = false }
  }
  async function fetchCircuitBreakerStatus() {
    try { const res = await importApi.getCircuitBreakerStatus(); circuitBreakerStatus.value = res.data.data }
    catch (e) { /* silent */ }
  }

  async function fetchExportPolicies() {
    try { const res = await exportApi.getExportPolicies(); exportPolicies.value = res.data.data }
    catch (e) { error.value = e.response?.data?.message || 'Failed to load policies' }
  }
  async function updateExportPolicy(data) {
    loading.value = true; error.value = null
    try { await exportApi.updateExportPolicies(data); successMessage.value = 'Policy updated' }
    catch (e) { error.value = e.response?.data?.message || 'Failed to update policy' }
    finally { loading.value = false }
  }
  async function requestExport(type, data) {
    loading.value = true; error.value = null
    try {
      let res
      if (type === 'roster') res = await exportApi.exportRoster(data)
      else if (type === 'finance') res = await exportApi.exportFinanceReport(data)
      successMessage.value = 'Export generated'
      return res.data.data
    } catch (e) { error.value = e.response?.data?.message || 'Export failed'; throw e }
    finally { loading.value = false }
  }

  function clearMessages() { error.value = null; successMessage.value = null }

  return { users, auditLogs, auditPagination, currentAuditLog, backups, retentionStatus, securitySettings, importSources, importJobs, circuitBreakerStatus, exportPolicies, loading, error, successMessage, fetchUsers, createUser, updateUser, fetchAuditLogs, fetchAuditLog, triggerAuditExport, fetchBackups, triggerBackup, fetchRetentionStatus, fetchSecuritySettings, updateSecuritySettings, fetchImportSources, fetchImportJobs, triggerImportCrawl, fetchCircuitBreakerStatus, fetchExportPolicies, updateExportPolicy, requestExport, clearMessages }
})
