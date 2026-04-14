<template>
  <div class="finance-dashboard-view">
    <h1>Finance Dashboard</h1>

    <div v-if="!authStore.hasAnyRole(['FINANCE_MANAGER', 'SYSTEM_ADMIN'])" class="access-denied">
      <h2>Access Denied</h2>
      <p>You do not have permission to view the finance dashboard.</p>
    </div>

    <template v-else>
      <LoadingSpinner v-if="financeStore.loading && !initialized" message="Loading finance data..." />

      <ErrorAlert
        v-if="financeStore.error"
        :message="financeStore.error"
        title="Error"
        :retryable="true"
        @retry="loadAll"
        @dismiss="financeStore.clearMessages()"
      />

      <div v-if="financeStore.successMessage" class="success-alert" role="status">
        {{ financeStore.successMessage }}
        <button class="btn-dismiss" @click="financeStore.clearMessages()">Dismiss</button>
      </div>

      <template v-if="initialized">
        <!-- Quick Links -->
        <div class="quick-links">
          <router-link to="/finance/periods" class="link-card">
            <span class="link-icon">&#128197;</span>
            <span class="link-label">Accounting Periods</span>
          </router-link>
          <router-link to="/finance/allocations" class="link-card">
            <span class="link-icon">&#128200;</span>
            <span class="link-label">Allocations</span>
          </router-link>
        </div>

        <!-- Cost Centers Summary -->
        <div class="summary-cards">
          <div class="summary-card">
            <div class="summary-value">{{ activeCostCentersCount }}</div>
            <div class="summary-label">Active Cost Centers</div>
          </div>
          <div class="summary-card">
            <div class="summary-value">{{ openPeriodsCount }}</div>
            <div class="summary-label">Open Periods</div>
          </div>
          <div class="summary-card">
            <div class="summary-value">{{ financeStore.periods.length }}</div>
            <div class="summary-label">Total Periods</div>
          </div>
        </div>

        <!-- Active Periods Table -->
        <section class="section">
          <h2>Accounting Periods</h2>
          <div v-if="financeStore.periods.length === 0" class="empty-state">
            <p>No accounting periods found. Create one below.</p>
          </div>
          <table v-else class="data-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Start Date</th>
                <th>End Date</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="period in financeStore.periods" :key="period.id">
                <td>{{ period.name }}</td>
                <td>{{ formatDate(period.startDate) }}</td>
                <td>{{ formatDate(period.endDate) }}</td>
                <td>
                  <span class="badge" :class="periodStatusClass(period.status)">
                    {{ period.status }}
                  </span>
                </td>
                <td>
                  <button
                    v-if="period.status === 'OPEN'"
                    class="btn-sm btn-close-period"
                    :disabled="financeStore.loading"
                    @click="closePeriod(period)"
                  >Close Period</button>
                </td>
              </tr>
            </tbody>
          </table>
        </section>

        <!-- Create Period Form -->
        <section class="section">
          <h2>Create Accounting Period</h2>
          <form class="inline-form" @submit.prevent="handleCreatePeriod">
            <div class="form-group">
              <label for="period-name">Name</label>
              <input id="period-name" v-model.trim="newPeriod.name" type="text" required placeholder="Q1 2026" />
            </div>
            <div class="form-group">
              <label for="period-start">Start Date</label>
              <input id="period-start" v-model="newPeriod.startDate" type="date" required />
            </div>
            <div class="form-group">
              <label for="period-end">End Date</label>
              <input id="period-end" v-model="newPeriod.endDate" type="date" required />
            </div>
            <button type="submit" class="btn-primary" :disabled="financeStore.loading">
              {{ financeStore.loading ? 'Saving...' : 'Create Period' }}
            </button>
          </form>
        </section>
      </template>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useFinanceStore } from '@/stores/finance'
import { useAuthStore } from '@/stores/auth'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'
import apiClient from '@/api/client'

const financeStore = useFinanceStore()
const authStore = useAuthStore()

const initialized = ref(false)

const newPeriod = ref({ name: '', startDate: '', endDate: '' })

const activeCostCentersCount = computed(() =>
  financeStore.costCenters.filter(c => c.active !== false).length
)

const openPeriodsCount = computed(() =>
  financeStore.periods.filter(p => p.status === 'OPEN').length
)

async function loadAll() {
  await Promise.all([
    financeStore.fetchPeriods(),
    financeStore.fetchCostCenters()
  ])
  initialized.value = true
}

async function handleCreatePeriod() {
  await financeStore.createPeriod({ ...newPeriod.value })
  if (!financeStore.error) {
    newPeriod.value = { name: '', startDate: '', endDate: '' }
  }
}

async function closePeriod(period) {
  financeStore.loading = true
  financeStore.error = null
  try {
    await apiClient.put(`/finance/periods/${period.id}/close`)
    period.status = 'CLOSED'
    financeStore.successMessage = `Period "${period.name}" closed`
  } catch (e) {
    financeStore.error = e.response?.data?.message || 'Failed to close period'
  } finally {
    financeStore.loading = false
  }
}

function formatDate(iso) {
  if (!iso) return '--'
  try {
    return new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })
  } catch { return iso }
}

function periodStatusClass(status) {
  switch (status) {
    case 'OPEN': return 'badge-green'
    case 'CLOSED': return 'badge-gray'
    case 'LOCKED': return 'badge-red'
    default: return ''
  }
}

onMounted(() => {
  if (authStore.hasAnyRole(['FINANCE_MANAGER', 'SYSTEM_ADMIN'])) {
    loadAll()
  }
})
</script>

<style scoped>
.finance-dashboard-view {
  max-width: 1000px;
  margin: 0 auto;
  padding: 24px;
}

.finance-dashboard-view h1 {
  margin: 0 0 20px;
  color: #1a237e;
}

.finance-dashboard-view h2 {
  margin: 0 0 12px;
  color: #1a237e;
  font-size: 1.15rem;
}

.access-denied {
  text-align: center;
  padding: 60px 24px;
  color: #c62828;
}

.access-denied h2 {
  color: #c62828;
}

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

.quick-links {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
}

.link-card {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  background: #e8eaf6;
  border: 1px solid #c5cae9;
  border-radius: 8px;
  text-decoration: none;
  color: #1a237e;
  font-weight: 600;
  transition: background 0.2s;
}

.link-card:hover {
  background: #c5cae9;
}

.link-icon {
  font-size: 1.5rem;
}

.summary-cards {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
}

.summary-card {
  flex: 1;
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 20px;
  text-align: center;
}

.summary-value {
  font-size: 2rem;
  font-weight: 700;
  color: #1a237e;
}

.summary-label {
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

.data-table tr:last-child td {
  border-bottom: none;
}

.badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 700;
  text-transform: uppercase;
}

.badge-green { background: #e8f5e9; color: #2e7d32; }
.badge-gray { background: #f5f5f5; color: #757575; }
.badge-red { background: #ffebee; color: #c62828; }

.btn-sm {
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid;
}

.btn-close-period {
  background: #fff;
  border-color: #f57f17;
  color: #f57f17;
}

.btn-close-period:hover:not(:disabled) {
  background: #fff8e1;
}

.btn-close-period:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.inline-form {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  flex-wrap: wrap;
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
.form-group select,
.form-group textarea {
  padding: 8px 10px;
  border: 1px solid #ccc;
  border-radius: 4px;
  font-size: 0.9rem;
}

.form-group input:focus,
.form-group select:focus,
.form-group textarea:focus {
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

.btn-primary:hover:not(:disabled) {
  background: #283593;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.empty-state {
  text-align: center;
  padding: 32px 24px;
  color: #666;
}
</style>
