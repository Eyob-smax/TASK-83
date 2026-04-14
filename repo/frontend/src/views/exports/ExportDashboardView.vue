<template>
  <div class="export-dashboard-view">
    <h1>Export Dashboard</h1>

    <div v-if="!authStore.hasAnyRole(['EVENT_STAFF', 'FINANCE_MANAGER', 'SYSTEM_ADMIN'])" class="access-denied">
      <h2>Access Denied</h2>
      <p>You do not have permission to access export operations.</p>
    </div>

    <template v-else>
      <LoadingSpinner v-if="adminStore.loading && !initialized" message="Loading export data..." />

      <ErrorAlert
        v-if="adminStore.error"
        :message="adminStore.error"
        title="Error"
        :retryable="true"
        @retry="loadPolicies"
        @dismiss="adminStore.clearMessages()"
      />

      <div v-if="adminStore.successMessage" class="success-alert" role="status">
        {{ adminStore.successMessage }}
        <button class="btn-dismiss" @click="adminStore.clearMessages()">Dismiss</button>
      </div>

      <template v-if="initialized">
        <!-- Restriction Notice -->
        <div class="restriction-notice">
          Downloads are watermarked with operator identity and timestamp.
        </div>

        <!-- Export Actions Section -->
        <section class="section">
          <h2>Export Actions</h2>
          <div class="export-actions-grid">
            <!-- Export Roster -->
            <div class="export-card">
              <h3>Export Roster</h3>
              <p>Generate a session attendance roster export.</p>
              <form @submit.prevent="handleExportRoster">
                <div class="form-group">
                  <label for="roster-session">Session ID</label>
                  <input id="roster-session" v-model.trim="rosterSessionId" type="text" required placeholder="Enter session ID" />
                </div>
                <button type="submit" class="btn-primary" :disabled="adminStore.loading">
                  {{ exportingRoster ? 'Exporting...' : 'Export Roster' }}
                </button>
              </form>
              <div v-if="rosterResult" class="export-result" :class="rosterResult.error ? 'result-error' : 'result-success'">
                <template v-if="!rosterResult.error">
                  <p>Job ID: <span class="mono">{{ rosterResult.id }}</span></p>
                  <p>Status: <span class="badge badge-green">{{ rosterResult.status || 'COMPLETE' }}</span></p>
                  <a v-if="rosterResult.downloadUrl || rosterResult.id" :href="rosterResult.downloadUrl || `/api/exports/${rosterResult.id}/download`" class="btn-download" download>Download</a>
                </template>
                <template v-else>
                  <p>{{ rosterResult.message }}</p>
                </template>
              </div>
            </div>

            <!-- Export Finance Report -->
            <div class="export-card">
              <h3>Export Finance Report</h3>
              <p>Generate a finance report for an accounting period.</p>
              <form @submit.prevent="handleExportFinance">
                <div class="form-group">
                  <label for="finance-period">Period ID</label>
                  <input id="finance-period" v-model.trim="financePeriodId" type="text" required placeholder="Enter period ID" />
                </div>
                <button type="submit" class="btn-primary" :disabled="adminStore.loading">
                  {{ exportingFinance ? 'Exporting...' : 'Export Finance Report' }}
                </button>
              </form>
              <div v-if="financeResult" class="export-result" :class="financeResult.error ? 'result-error' : 'result-success'">
                <template v-if="!financeResult.error">
                  <p>Job ID: <span class="mono">{{ financeResult.id }}</span></p>
                  <p>Status: <span class="badge badge-green">{{ financeResult.status || 'COMPLETE' }}</span></p>
                  <a v-if="financeResult.downloadUrl || financeResult.id" :href="financeResult.downloadUrl || `/api/exports/${financeResult.id}/download`" class="btn-download" download>Download</a>
                </template>
                <template v-else>
                  <p>{{ financeResult.message }}</p>
                </template>
              </div>
            </div>

            <!-- Export Audit Logs -->
            <div class="export-card">
              <h3>Export Audit Logs</h3>
              <p>Generate an audit log export for a date range.</p>
              <form @submit.prevent="handleExportAudit">
                <div class="form-group">
                  <label for="audit-from">Date From</label>
                  <input id="audit-from" v-model="auditDateFrom" type="date" required />
                </div>
                <div class="form-group">
                  <label for="audit-to">Date To</label>
                  <input id="audit-to" v-model="auditDateTo" type="date" required />
                </div>
                <button type="submit" class="btn-primary" :disabled="adminStore.loading">
                  {{ exportingAudit ? 'Exporting...' : 'Export Audit Logs' }}
                </button>
              </form>
              <div v-if="auditResult" class="export-result" :class="auditResult.error ? 'result-error' : 'result-success'">
                <template v-if="!auditResult.error">
                  <p>Job ID: <span class="mono">{{ auditResult.id || auditResult.exportId }}</span></p>
                  <p>Status: <span class="badge badge-green">{{ auditResult.status || 'COMPLETE' }}</span></p>
                  <a v-if="auditResult.downloadUrl || auditResult.exportId || auditResult.id" :href="auditResult.downloadUrl || `/api/audit/exports/${auditResult.exportId || auditResult.id}/download`" class="btn-download" download>Download</a>
                </template>
                <template v-else>
                  <p>{{ auditResult.message }}</p>
                </template>
              </div>
            </div>
          </div>
        </section>

        <!-- Watermark Policies Table -->
        <section class="section">
          <h2>Watermark Policies</h2>
          <div v-if="adminStore.exportPolicies.length === 0" class="empty-state">
            <p>No watermark policies configured.</p>
          </div>
          <table v-else class="data-table">
            <thead>
              <tr>
                <th>Report Type</th>
                <th>Role</th>
                <th>Download Allowed</th>
                <th>Watermark Template</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="policy in adminStore.exportPolicies" :key="policy.id || policy.reportType + policy.roleType">
                <td>{{ formatLabel(policy.reportType) }}</td>
                <td>
                  <span class="badge badge-role">{{ formatLabel(policy.roleType || policy.role) }}</span>
                </td>
                <td>
                  <span :class="policy.downloadAllowed !== false ? 'text-yes' : 'text-no'">
                    {{ policy.downloadAllowed !== false ? 'Yes' : 'No' }}
                  </span>
                </td>
                <td class="mono">{{ policy.watermarkTemplate || '--' }}</td>
              </tr>
            </tbody>
          </table>
        </section>
      </template>
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

const initialized = ref(false)

const rosterSessionId = ref('')
const financePeriodId = ref('')
const auditDateFrom = ref('')
const auditDateTo = ref('')

const exportingRoster = ref(false)
const exportingFinance = ref(false)
const exportingAudit = ref(false)

const rosterResult = ref(null)
const financeResult = ref(null)
const auditResult = ref(null)

async function loadPolicies() {
  await adminStore.fetchExportPolicies()
  initialized.value = true
}

async function handleExportRoster() {
  rosterResult.value = null
  exportingRoster.value = true
  try {
    const result = await adminStore.requestExport('roster', { sessionId: rosterSessionId.value })
    rosterResult.value = { ...result, error: false }
  } catch {
    rosterResult.value = { error: true, message: adminStore.error }
  } finally {
    exportingRoster.value = false
  }
}

async function handleExportFinance() {
  financeResult.value = null
  exportingFinance.value = true
  try {
    const result = await adminStore.requestExport('finance', { periodId: financePeriodId.value })
    financeResult.value = { ...result, error: false }
  } catch {
    financeResult.value = { error: true, message: adminStore.error }
  } finally {
    exportingFinance.value = false
  }
}

async function handleExportAudit() {
  auditResult.value = null
  exportingAudit.value = true
  try {
    const result = await adminStore.triggerAuditExport({
      dateFrom: auditDateFrom.value,
      dateTo: auditDateTo.value
    })
    auditResult.value = { ...result, error: false }
  } catch {
    auditResult.value = { error: true, message: adminStore.error }
  } finally {
    exportingAudit.value = false
  }
}

function formatLabel(val) {
  if (!val) return '--'
  return val.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())
}

onMounted(() => {
  if (authStore.hasAnyRole(['EVENT_STAFF', 'FINANCE_MANAGER', 'SYSTEM_ADMIN'])) {
    loadPolicies()
  }
})
</script>

<style scoped>
.export-dashboard-view {
  max-width: 1100px;
  margin: 0 auto;
  padding: 24px;
}

.export-dashboard-view h1 {
  margin: 0 0 20px;
  color: #1a237e;
}

.export-dashboard-view h2 {
  margin: 0 0 12px;
  color: #1a237e;
  font-size: 1.15rem;
}

.export-dashboard-view h3 {
  margin: 0 0 8px;
  color: #333;
  font-size: 1rem;
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
  justify-content: space-between;
  align-items: center;
}

.btn-dismiss {
  background: none;
  border: 1px solid #2e7d32;
  color: #2e7d32;
  padding: 4px 12px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.8rem;
}

.restriction-notice {
  background: #fff8e1;
  border: 1px solid #ffe082;
  border-radius: 4px;
  padding: 12px 16px;
  margin-bottom: 20px;
  color: #f57f17;
  font-weight: 600;
  font-size: 0.9rem;
}

.section {
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 20px;
}

.export-actions-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
}

.export-card {
  background: #fafafa;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 20px;
}

.export-card p {
  color: #666;
  font-size: 0.85rem;
  margin: 0 0 12px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
}

.form-group label {
  font-size: 0.8rem;
  font-weight: 600;
  color: #555;
}

.form-group input {
  padding: 8px 10px;
  border: 1px solid #ccc;
  border-radius: 4px;
  font-size: 0.9rem;
}

.form-group input:focus {
  outline: none;
  border-color: #1a237e;
  box-shadow: 0 0 0 2px rgba(26, 35, 126, 0.15);
}

.export-result {
  margin-top: 12px;
  padding: 10px 14px;
  border-radius: 4px;
  font-size: 0.85rem;
}

.export-result p {
  margin: 2px 0;
  color: inherit;
}

.result-success {
  background: #e8f5e9;
  border: 1px solid #a5d6a7;
  color: #2e7d32;
}

.result-error {
  background: #ffebee;
  border: 1px solid #ef9a9a;
  color: #c62828;
}

.btn-download {
  display: inline-block;
  margin-top: 8px;
  padding: 6px 14px;
  background: #1a237e;
  color: #fff;
  border-radius: 4px;
  text-decoration: none;
  font-weight: 600;
  font-size: 0.8rem;
  transition: background 0.2s;
}

.btn-download:hover {
  background: #283593;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
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

.data-table tr:last-child td { border-bottom: none; }

.mono { font-family: monospace; }

.badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 700;
  text-transform: uppercase;
}

.badge-green { background: #e8f5e9; color: #2e7d32; }
.badge-role { background: #e8eaf6; color: #1a237e; }

.text-yes { color: #2e7d32; font-weight: 600; }
.text-no { color: #c62828; font-weight: 600; }

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

.empty-state {
  text-align: center;
  padding: 32px 24px;
  color: #666;
}
</style>
