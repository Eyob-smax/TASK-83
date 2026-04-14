<template>
  <div class="backup-status-view">
    <h1>Backup Status</h1>

    <div v-if="!authStore.hasRole('SYSTEM_ADMIN')" class="access-denied">
      <h2>Access Denied</h2>
      <p>Only System Administrators can view backup status.</p>
    </div>

    <template v-else>
      <LoadingSpinner v-if="adminStore.loading && !initialized" message="Loading backup data..." />

      <ErrorAlert
        v-if="adminStore.error"
        :message="adminStore.error"
        title="Error"
        :retryable="true"
        @retry="loadAll"
        @dismiss="adminStore.clearMessages()"
      />

      <div v-if="adminStore.successMessage" class="success-alert" role="status">
        {{ adminStore.successMessage }}
        <button class="btn-dismiss" @click="adminStore.clearMessages()">Dismiss</button>
      </div>

      <template v-if="initialized">
        <!-- Retention Metrics Cards -->
        <div class="metrics-cards">
          <div class="metric-card">
            <div class="metric-value">{{ totalBackups }}</div>
            <div class="metric-label">Total Backups</div>
          </div>
          <div class="metric-card metric-success">
            <div class="metric-value">{{ completedBackups }}</div>
            <div class="metric-label">Completed</div>
          </div>
          <div class="metric-card metric-danger">
            <div class="metric-value">{{ failedBackups }}</div>
            <div class="metric-label">Failed</div>
          </div>
          <div class="metric-card">
            <div class="metric-value">{{ retentionDays }} days</div>
            <div class="metric-label">Retention Period</div>
          </div>
        </div>

        <!-- Manual Backup Trigger -->
        <section class="section">
          <div class="section-header">
            <h2>Manual Backup</h2>
            <button
              class="btn-primary"
              :disabled="backupTriggering"
              @click="handleTriggerBackup"
            >
              {{ backupTriggering ? 'Triggering...' : 'Trigger Backup Now' }}
            </button>
          </div>
          <p class="section-description">
            Initiate a manual backup of all system data. The backup will appear in the history below once scheduled.
          </p>
        </section>

        <!-- Backup History Table -->
        <section class="section">
          <h2>Backup History</h2>
          <div v-if="adminStore.backups.length === 0" class="empty-state">
            <p>No backup records found.</p>
          </div>
          <table v-else class="data-table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Status</th>
                <th>File Size</th>
                <th>Triggered By</th>
                <th>Retention Expires</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="backup in adminStore.backups" :key="backup.id">
                <td>{{ formatDateTime(backup.createdAt || backup.startedAt) }}</td>
                <td>
                  <span class="badge" :class="backupStatusClass(backup.status)">
                    {{ backup.status }}
                  </span>
                </td>
                <td>{{ formatFileSize(backup.fileSize) }}</td>
                <td>{{ backup.triggeredBy || backup.operatorId || 'System' }}</td>
                <td>{{ formatDate(backup.retentionExpiresAt) }}</td>
              </tr>
            </tbody>
          </table>
        </section>
      </template>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useAdminStore } from '@/stores/admin'
import { useAuthStore } from '@/stores/auth'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'

const adminStore = useAdminStore()
const authStore = useAuthStore()

const initialized = ref(false)
const backupTriggering = ref(false)

const totalBackups = computed(() => adminStore.backups.length)
const completedBackups = computed(() =>
  adminStore.backups.filter(b => b.status === 'COMPLETED').length
)
const failedBackups = computed(() =>
  adminStore.backups.filter(b => b.status === 'FAILED').length
)
const retentionDays = computed(() =>
  adminStore.retentionStatus?.retentionDays || 30
)

async function loadAll() {
  await Promise.all([
    adminStore.fetchBackups(),
    adminStore.fetchRetentionStatus()
  ])
  initialized.value = true
}

async function handleTriggerBackup() {
  backupTriggering.value = true
  await adminStore.triggerBackup()
  backupTriggering.value = false
}

function formatDateTime(iso) {
  if (!iso) return '--'
  try {
    return new Date(iso).toLocaleString(undefined, { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
  } catch { return iso }
}

function formatDate(iso) {
  if (!iso) return '--'
  try {
    return new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })
  } catch { return iso }
}

function formatFileSize(bytes) {
  if (bytes == null) return '--'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB'
}

function backupStatusClass(status) {
  switch (status) {
    case 'COMPLETED': return 'badge-green'
    case 'FAILED': return 'badge-red'
    case 'IN_PROGRESS': return 'badge-yellow'
    case 'SCHEDULED': return 'badge-blue'
    default: return 'badge-gray'
  }
}

onMounted(() => {
  if (authStore.hasRole('SYSTEM_ADMIN')) {
    loadAll()
  }
})
</script>

<style scoped>
.backup-status-view {
  max-width: 1000px;
  margin: 0 auto;
  padding: 24px;
}

.backup-status-view h1 {
  margin: 0 0 20px;
  color: #1a237e;
}

.backup-status-view h2 {
  margin: 0 0 12px;
  color: #1a237e;
  font-size: 1.15rem;
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

.metrics-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.metric-card {
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 20px;
  text-align: center;
}

.metric-card.metric-success {
  border-color: #a5d6a7;
}

.metric-card.metric-danger {
  border-color: #ef9a9a;
}

.metric-value {
  font-size: 2rem;
  font-weight: 700;
  color: #1a237e;
}

.metric-success .metric-value { color: #2e7d32; }
.metric-danger .metric-value { color: #c62828; }

.metric-label {
  font-size: 0.85rem;
  color: #666;
  margin-top: 4px;
}

.section {
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 20px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.section-header h2 {
  margin: 0;
}

.section-description {
  color: #666;
  font-size: 0.9rem;
  margin: 0;
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

.badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 700;
  text-transform: uppercase;
}

.badge-green { background: #e8f5e9; color: #2e7d32; }
.badge-red { background: #ffebee; color: #c62828; }
.badge-yellow { background: #fff8e1; color: #f57f17; }
.badge-blue { background: #e3f2fd; color: #1565c0; }
.badge-gray { background: #f5f5f5; color: #757575; }

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
  padding: 40px 24px;
  color: #666;
}
</style>
