<template>
  <div class="audit-log-view">
    <h1>Audit Log</h1>

    <div v-if="!authStore.hasRole('SYSTEM_ADMIN')" class="access-denied">
      <h2>Access Denied</h2>
      <p>Only System Administrators can view audit logs.</p>
    </div>

    <template v-else>
      <ErrorAlert
        v-if="adminStore.error"
        :message="adminStore.error"
        title="Error"
        :retryable="true"
        @retry="searchLogs"
        @dismiss="adminStore.clearMessages()"
      />

      <div v-if="adminStore.successMessage" class="success-alert" role="status">
        {{ adminStore.successMessage }}
        <span v-if="exportDownloadUrl">
          - <a :href="exportDownloadUrl" class="download-link" download>Download CSV</a>
        </span>
        <button class="btn-dismiss" @click="adminStore.clearMessages()">Dismiss</button>
      </div>

      <!-- Filter Bar -->
      <section class="filter-bar">
        <div class="form-group">
          <label for="filter-from">Date From</label>
          <input id="filter-from" v-model="filters.dateFrom" type="date" />
        </div>
        <div class="form-group">
          <label for="filter-to">Date To</label>
          <input id="filter-to" v-model="filters.dateTo" type="date" />
        </div>
        <div class="form-group">
          <label for="filter-action">Action Type</label>
          <select id="filter-action" v-model="filters.actionType">
            <option value="">All Actions</option>
            <option v-for="action in auditActionTypes" :key="action" :value="action">
              {{ formatLabel(action) }}
            </option>
          </select>
        </div>
        <div class="form-group">
          <label for="filter-operator">Operator ID</label>
          <input id="filter-operator" v-model.trim="filters.operatorId" type="text" placeholder="User ID..." />
        </div>
        <button class="btn-primary" :disabled="adminStore.loading" @click="searchLogs">
          {{ adminStore.loading ? 'Searching...' : 'Search' }}
        </button>
        <button class="btn-secondary" :disabled="adminStore.loading" @click="handleExport">
          Export CSV
        </button>
      </section>

      <!-- Results -->
      <section class="section">
        <LoadingSpinner v-if="adminStore.loading && adminStore.auditLogs.length === 0" message="Loading audit logs..." />

        <div v-else-if="adminStore.auditLogs.length === 0" class="empty-state">
          <p>No audit log entries found. Adjust your filters and search again.</p>
        </div>

        <template v-else>
          <table class="data-table">
            <thead>
              <tr>
                <th>Timestamp</th>
                <th>Action</th>
                <th>Operator</th>
                <th>Entity Type</th>
                <th>Entity ID</th>
                <th>Description</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="log in adminStore.auditLogs" :key="log.id">
                <tr
                  class="log-row"
                  :class="{ 'row-selected': expandedLogId === log.id }"
                  @click="toggleExpand(log)"
                >
                  <td>{{ formatDateTime(log.timestamp || log.createdAt) }}</td>
                  <td>
                    <span class="badge badge-action">{{ formatLabel(log.actionType || log.action) }}</span>
                  </td>
                  <td>{{ log.operatorId || log.operatorName || '--' }}</td>
                  <td>{{ log.entityType || '--' }}</td>
                  <td class="mono">{{ log.entityId || '--' }}</td>
                  <td>{{ log.description || '--' }}</td>
                </tr>
                <tr v-if="expandedLogId === log.id" class="detail-row">
                  <td colspan="6">
                    <div class="detail-panel">
                      <h4>Field-Level Changes</h4>
                      <LoadingSpinner v-if="detailLoading" message="Loading details..." />
                      <div v-else-if="!logDetail || !logDetail.fieldDiffs || logDetail.fieldDiffs.length === 0" class="empty-detail">
                        No field-level diffs available for this entry.
                      </div>
                      <table v-else class="diff-table">
                        <thead>
                          <tr>
                            <th>Field</th>
                            <th>Old Value</th>
                            <th>New Value</th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr v-for="(diff, idx) in logDetail.fieldDiffs" :key="idx">
                            <td class="mono">{{ diff.field }}</td>
                            <td class="val-old">{{ diff.oldValue ?? '--' }}</td>
                            <td class="val-new">{{ diff.newValue ?? '--' }}</td>
                          </tr>
                        </tbody>
                      </table>
                    </div>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>

          <!-- Pagination -->
          <div class="pagination">
            <button
              class="btn-page"
              :disabled="currentPage <= 0"
              @click="goToPage(currentPage - 1)"
            >Previous</button>
            <span class="page-info">
              Page {{ currentPage + 1 }} of {{ adminStore.auditPagination.totalPages || 1 }}
              ({{ adminStore.auditPagination.totalElements }} total)
            </span>
            <button
              class="btn-page"
              :disabled="currentPage >= (adminStore.auditPagination.totalPages - 1)"
              @click="goToPage(currentPage + 1)"
            >Next</button>
          </div>
        </template>
      </section>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useAdminStore } from '@/stores/admin'
import { useAuthStore } from '@/stores/auth'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'

const adminStore = useAdminStore()
const authStore = useAuthStore()

const filters = ref({
  dateFrom: '',
  dateTo: '',
  actionType: '',
  operatorId: ''
})

const currentPage = ref(0)
const expandedLogId = ref(null)
const logDetail = ref(null)
const detailLoading = ref(false)
const exportDownloadUrl = ref(null)

const auditActionTypes = [
  'USER_LOGIN',
  'USER_LOGOUT',
  'USER_CREATED',
  'USER_UPDATED',
  'USER_LOCKED',
  'ROLE_CHANGED',
  'SESSION_CREATED',
  'SESSION_UPDATED',
  'SESSION_CANCELLED',
  'REGISTRATION_CREATED',
  'REGISTRATION_CANCELLED',
  'CHECKIN_PERFORMED',
  'CHECKIN_DENIED',
  'PERIOD_CREATED',
  'PERIOD_CLOSED',
  'POSTING_EXECUTED',
  'POSTING_REVERSED',
  'RULE_CREATED',
  'RULE_UPDATED',
  'IMPORT_TRIGGERED',
  'IMPORT_COMPLETED',
  'EXPORT_GENERATED',
  'BACKUP_TRIGGERED',
  'SETTINGS_UPDATED'
]

function buildParams() {
  const params = { page: currentPage.value }
  if (filters.value.dateFrom) params.dateFrom = filters.value.dateFrom
  if (filters.value.dateTo) params.dateTo = filters.value.dateTo
  if (filters.value.actionType) params.actionType = filters.value.actionType
  if (filters.value.operatorId) params.operatorId = filters.value.operatorId
  return params
}

function searchLogs() {
  currentPage.value = 0
  adminStore.fetchAuditLogs(buildParams())
}

function goToPage(page) {
  currentPage.value = page
  adminStore.fetchAuditLogs(buildParams())
}

async function toggleExpand(log) {
  if (expandedLogId.value === log.id) {
    expandedLogId.value = null
    logDetail.value = null
    return
  }
  expandedLogId.value = log.id
  logDetail.value = null
  detailLoading.value = true
  await adminStore.fetchAuditLog(log.id)
  logDetail.value = adminStore.currentAuditLog
  detailLoading.value = false
}

async function handleExport() {
  exportDownloadUrl.value = null
  try {
    const result = await adminStore.triggerAuditExport(buildParams())
    if (result && result.exportId) {
      exportDownloadUrl.value = `/api/audit/exports/${result.exportId}/download`
    }
  } catch {
    // error handled by store
  }
}

function formatLabel(val) {
  if (!val) return '--'
  return val.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())
}

function formatDateTime(iso) {
  if (!iso) return '--'
  try {
    return new Date(iso).toLocaleString(undefined, { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit' })
  } catch { return iso }
}

onMounted(() => {
  if (authStore.hasRole('SYSTEM_ADMIN')) {
    adminStore.fetchAuditLogs({ page: 0 })
  }
})
</script>

<style scoped>
.audit-log-view {
  max-width: 1100px;
  margin: 0 auto;
  padding: 24px;
}

.audit-log-view h1 {
  margin: 0 0 20px;
  color: #1a237e;
}

.access-denied {
  text-align: center;
  padding: 60px 24px;
  color: #c62828;
}

.access-denied h2 { color: #c62828; }

.success-alert {
  background: #e8f5e9;
  border: 1px solid #a5d6a7;
  border-radius: 4px;
  padding: 12px 16px;
  margin-bottom: 16px;
  color: #2e7d32;
  display: flex;
  align-items: center;
  gap: 8px;
}

.download-link {
  color: #1a237e;
  font-weight: 600;
  text-decoration: underline;
}

.btn-dismiss {
  margin-left: auto;
  background: none;
  border: 1px solid #2e7d32;
  color: #2e7d32;
  padding: 4px 12px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.8rem;
}

.filter-bar {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  flex-wrap: wrap;
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 16px 20px;
  margin-bottom: 20px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.form-group label {
  font-size: 0.8rem;
  font-weight: 600;
  color: #555;
}

.form-group input,
.form-group select {
  padding: 8px 10px;
  border: 1px solid #ccc;
  border-radius: 4px;
  font-size: 0.9rem;
}

.form-group input:focus,
.form-group select:focus {
  outline: none;
  border-color: #1a237e;
  box-shadow: 0 0 0 2px rgba(26, 35, 126, 0.15);
}

.btn-primary {
  padding: 8px 20px;
  background: #1a237e;
  color: #fff;
  border: none;
  border-radius: 4px;
  font-weight: 600;
  font-size: 0.9rem;
  cursor: pointer;
  transition: background 0.2s;
}

.btn-primary:hover:not(:disabled) { background: #283593; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }

.btn-secondary {
  padding: 8px 20px;
  background: #fff;
  color: #1a237e;
  border: 1px solid #1a237e;
  border-radius: 4px;
  font-weight: 600;
  font-size: 0.9rem;
  cursor: pointer;
  transition: background 0.2s;
}

.btn-secondary:hover:not(:disabled) { background: #e8eaf6; }
.btn-secondary:disabled { opacity: 0.5; cursor: not-allowed; }

.section {
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 20px;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85rem;
}

.data-table th,
.data-table td {
  padding: 10px 12px;
  text-align: left;
  border-bottom: 1px solid #e0e0e0;
}

.data-table th {
  background: #f5f5f5;
  font-weight: 600;
  color: #333;
}

.log-row {
  cursor: pointer;
  transition: background 0.15s;
}

.log-row:hover {
  background: #f5f5f5;
}

.row-selected {
  background: #e8eaf6;
}

.detail-row td {
  padding: 0;
  border-bottom: 2px solid #1a237e;
}

.detail-panel {
  padding: 16px 20px;
  background: #fafafa;
}

.detail-panel h4 {
  margin: 0 0 10px;
  color: #1a237e;
  font-size: 0.95rem;
}

.empty-detail {
  color: #666;
  font-size: 0.85rem;
  padding: 8px 0;
}

.diff-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85rem;
}

.diff-table th,
.diff-table td {
  padding: 6px 10px;
  border-bottom: 1px solid #e0e0e0;
  text-align: left;
}

.diff-table th {
  background: #f0f0f0;
  font-weight: 600;
}

.val-old { color: #c62828; }
.val-new { color: #2e7d32; }

.mono { font-family: monospace; }

.badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
}

.badge-action { background: #e8eaf6; color: #1a237e; }

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  margin-top: 20px;
  padding: 12px 0;
}

.btn-page {
  padding: 8px 16px;
  border: 1px solid #ccc;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  font-size: 0.875rem;
  color: #1a237e;
  font-weight: 600;
  transition: background 0.2s;
}

.btn-page:hover:not(:disabled) { background: #e8eaf6; }
.btn-page:disabled { opacity: 0.4; cursor: not-allowed; }

.page-info {
  font-size: 0.85rem;
  color: #666;
}

.empty-state {
  text-align: center;
  padding: 40px 24px;
  color: #666;
}
</style>
