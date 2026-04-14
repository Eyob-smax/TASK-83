<template>
  <div class="import-dashboard-view">
    <h1>Import Dashboard</h1>

    <div v-if="!authStore.hasAnyRole(['EVENT_STAFF', 'SYSTEM_ADMIN'])" class="access-denied">
      <h2>Access Denied</h2>
      <p>Only Staff and Administrators can access the import dashboard.</p>
    </div>

    <template v-else>
      <LoadingSpinner v-if="adminStore.loading && !initialized" message="Loading import data..." />

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
        <!-- Import Sources Table -->
        <section class="section">
          <h2>Import Sources</h2>
          <div v-if="adminStore.importSources.length === 0" class="empty-state">
            <p>No import sources configured.</p>
          </div>
          <table v-else class="data-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Folder Path</th>
                <th>File Pattern</th>
                <th>Mode</th>
                <th>Concurrency</th>
                <th>Timeout</th>
                <th>CB Threshold</th>
                <th>Active</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="source in adminStore.importSources" :key="source.id">
                <td>{{ source.name }}</td>
                <td class="mono cell-truncate" :title="source.folderPath">{{ source.folderPath }}</td>
                <td class="mono">{{ source.filePattern || '*' }}</td>
                <td>{{ source.mode || '--' }}</td>
                <td>{{ source.concurrencyCap ?? '--' }}</td>
                <td>{{ source.timeout ? source.timeout + 's' : '--' }}</td>
                <td>{{ source.circuitBreakerThreshold ?? '--' }}</td>
                <td>
                  <span class="badge" :class="source.active !== false ? 'badge-green' : 'badge-gray'">
                    {{ source.active !== false ? 'Active' : 'Inactive' }}
                  </span>
                </td>
                <td>
                  <button
                    class="btn-sm btn-crawl"
                    :disabled="adminStore.loading"
                    @click="openCrawlModal(source)"
                  >Trigger Crawl</button>
                </td>
              </tr>
            </tbody>
          </table>
        </section>

        <!-- Crawl Jobs Table -->
        <section class="section">
          <h2>Crawl Jobs</h2>
          <div v-if="adminStore.importJobs.length === 0" class="empty-state">
            <p>No crawl jobs found.</p>
          </div>
          <table v-else class="data-table">
            <thead>
              <tr>
                <th>Source</th>
                <th>Trace ID</th>
                <th>Status</th>
                <th>Files Processed</th>
                <th>Records Imported</th>
                <th>Records Failed</th>
                <th>Checkpoint</th>
                <th>Started</th>
                <th>Completed</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="job in adminStore.importJobs" :key="job.id">
                <td>{{ sourceName(job.sourceId) }}</td>
                <td class="mono cell-truncate" :title="job.traceId">{{ job.traceId || '--' }}</td>
                <td>
                  <span class="badge" :class="jobStatusClass(job.status)">
                    {{ job.status }}
                  </span>
                </td>
                <td>{{ job.filesProcessed ?? 0 }}</td>
                <td>{{ job.recordsImported ?? 0 }}</td>
                <td>{{ job.recordsFailed ?? 0 }}</td>
                <td class="mono cell-truncate" :title="job.checkpointMarker">{{ job.checkpointMarker || '--' }}</td>
                <td>{{ formatDateTime(job.startedAt) }}</td>
                <td>{{ formatDateTime(job.completedAt) }}</td>
              </tr>
            </tbody>
          </table>
        </section>

        <!-- Circuit Breaker Status Panel -->
        <section class="section">
          <h2>Circuit Breaker Status</h2>
          <div v-if="!adminStore.circuitBreakerStatus" class="empty-state">
            <p>Circuit breaker status unavailable.</p>
          </div>
          <div v-else class="cb-grid">
            <div
              v-for="(cb, sourceId) in adminStore.circuitBreakerStatus"
              :key="sourceId"
              class="cb-card"
              :class="cb.status === 'TRIPPED' || cb.tripped ? 'cb-tripped' : 'cb-ok'"
            >
              <div class="cb-source">{{ sourceName(sourceId) || sourceId }}</div>
              <div class="cb-status-label">
                <span class="badge" :class="cb.status === 'TRIPPED' || cb.tripped ? 'badge-red' : 'badge-green'">
                  {{ cb.status === 'TRIPPED' || cb.tripped ? 'TRIPPED' : 'OK' }}
                </span>
              </div>
              <div class="cb-failures">Failures: {{ cb.failureCount ?? cb.failures ?? 0 }}</div>
            </div>
          </div>
        </section>
      </template>

      <!-- Crawl Modal -->
      <div v-if="showCrawlModal" class="modal-overlay" @click.self="showCrawlModal = false">
        <div class="modal">
          <h3>Trigger Crawl - {{ crawlSource?.name }}</h3>
          <div class="form-group">
            <label for="crawl-mode">Crawl Mode</label>
            <select id="crawl-mode" v-model="crawlMode">
              <option value="INCREMENTAL">Incremental</option>
              <option value="FULL">Full</option>
            </select>
          </div>
          <div class="modal-actions">
            <button class="btn-secondary" @click="showCrawlModal = false">Cancel</button>
            <button class="btn-primary" :disabled="adminStore.loading" @click="executeCrawl">
              {{ adminStore.loading ? 'Triggering...' : 'Trigger' }}
            </button>
          </div>
        </div>
      </div>
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
const showCrawlModal = ref(false)
const crawlSource = ref(null)
const crawlMode = ref('INCREMENTAL')

async function loadAll() {
  await Promise.all([
    adminStore.fetchImportSources(),
    adminStore.fetchImportJobs(),
    adminStore.fetchCircuitBreakerStatus()
  ])
  initialized.value = true
}

function openCrawlModal(source) {
  crawlSource.value = source
  crawlMode.value = 'INCREMENTAL'
  showCrawlModal.value = true
}

async function executeCrawl() {
  await adminStore.triggerImportCrawl({
    sourceId: crawlSource.value.id,
    mode: crawlMode.value
  })
  showCrawlModal.value = false
  if (!adminStore.error) {
    await adminStore.fetchImportJobs()
  }
}

function sourceName(sourceId) {
  const s = adminStore.importSources.find(s => s.id === sourceId)
  return s ? s.name : ''
}

function formatDateTime(iso) {
  if (!iso) return '--'
  try {
    return new Date(iso).toLocaleString(undefined, { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
  } catch { return iso }
}

function jobStatusClass(status) {
  switch (status) {
    case 'COMPLETED': return 'badge-green'
    case 'FAILED': return 'badge-red'
    case 'RUNNING': return 'badge-yellow'
    case 'QUEUED': return 'badge-blue'
    case 'CIRCUIT_BROKEN': return 'badge-red'
    default: return 'badge-gray'
  }
}

onMounted(() => {
  if (authStore.hasAnyRole(['EVENT_STAFF', 'SYSTEM_ADMIN'])) {
    loadAll()
  }
})
</script>

<style scoped>
.import-dashboard-view {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
}

.import-dashboard-view h1 {
  margin: 0 0 20px;
  color: #1a237e;
}

.import-dashboard-view h2 {
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
  font-size: 0.8rem;
}

.data-table th,
.data-table td {
  padding: 8px 10px;
  text-align: left;
  border-bottom: 1px solid #e0e0e0;
}

.data-table th {
  background: #f5f5f5;
  font-weight: 600;
  color: #333;
  white-space: nowrap;
}

.data-table tr:last-child td { border-bottom: none; }

.mono { font-family: monospace; font-size: 0.8rem; }

.cell-truncate {
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
}

.badge-green { background: #e8f5e9; color: #2e7d32; }
.badge-red { background: #ffebee; color: #c62828; }
.badge-yellow { background: #fff8e1; color: #f57f17; }
.badge-blue { background: #e3f2fd; color: #1565c0; }
.badge-gray { background: #f5f5f5; color: #757575; }

.btn-sm {
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid;
}

.btn-crawl {
  background: #fff;
  border-color: #1a237e;
  color: #1a237e;
}

.btn-crawl:hover:not(:disabled) { background: #e8eaf6; }
.btn-crawl:disabled { opacity: 0.5; cursor: not-allowed; }

.cb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}

.cb-card {
  padding: 16px;
  border-radius: 8px;
  border: 1px solid #e0e0e0;
  text-align: center;
}

.cb-ok { border-color: #a5d6a7; background: #f9fdf9; }
.cb-tripped { border-color: #ef9a9a; background: #fff8f8; }

.cb-source {
  font-weight: 600;
  color: #333;
  margin-bottom: 8px;
}

.cb-status-label {
  margin-bottom: 4px;
}

.cb-failures {
  font-size: 0.85rem;
  color: #666;
}

/* Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  min-width: 360px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}

.modal h3 {
  margin: 0 0 16px;
  color: #1a237e;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 16px;
}

.form-group label {
  font-size: 0.8rem;
  font-weight: 600;
  color: #555;
}

.form-group select {
  padding: 8px 10px;
  border: 1px solid #ccc;
  border-radius: 4px;
  font-size: 0.9rem;
}

.form-group select:focus {
  outline: none;
  border-color: #1a237e;
  box-shadow: 0 0 0 2px rgba(26, 35, 126, 0.15);
}

.modal-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
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
  color: #666;
  border: 1px solid #ccc;
  border-radius: 4px;
  font-weight: 600;
  font-size: 0.9rem;
  cursor: pointer;
}

.btn-secondary:hover { background: #f5f5f5; }

.empty-state {
  text-align: center;
  padding: 32px 24px;
  color: #666;
}
</style>
