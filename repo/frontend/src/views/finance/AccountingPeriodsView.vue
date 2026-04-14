<template>
  <div class="accounting-periods-view">
    <h1>Accounting Periods &amp; Accounts</h1>

    <div v-if="!authStore.hasAnyRole(['FINANCE_MANAGER', 'SYSTEM_ADMIN'])" class="access-denied">
      <h2>Access Denied</h2>
      <p>You do not have permission to manage accounting periods.</p>
    </div>

    <template v-else>
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

      <!-- Periods Section -->
      <section class="section">
        <h2>Periods</h2>
        <LoadingSpinner v-if="periodsLoading" message="Loading periods..." />
        <div v-else-if="financeStore.periods.length === 0" class="empty-state">
          <p>No accounting periods found.</p>
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
                >Close</button>
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <!-- Chart of Accounts Section -->
      <section class="section">
        <h2>Chart of Accounts</h2>
        <LoadingSpinner v-if="accountsLoading" message="Loading accounts..." />
        <div v-else-if="financeStore.accounts.length === 0" class="empty-state">
          <p>No accounts found. Create one below.</p>
        </div>
        <table v-else class="data-table">
          <thead>
            <tr>
              <th>Code</th>
              <th>Name</th>
              <th>Type</th>
              <th>Parent</th>
              <th>Active</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="account in financeStore.accounts" :key="account.id">
              <td class="mono">{{ account.code }}</td>
              <td>{{ account.name }}</td>
              <td>{{ account.type }}</td>
              <td>{{ account.parentCode || '--' }}</td>
              <td>
                <span class="badge" :class="account.active !== false ? 'badge-green' : 'badge-gray'">
                  {{ account.active !== false ? 'Active' : 'Inactive' }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>

        <h3>Create Account</h3>
        <form class="inline-form" @submit.prevent="handleCreateAccount">
          <div class="form-group">
            <label for="acct-code">Code</label>
            <input id="acct-code" v-model.trim="newAccount.code" type="text" required placeholder="1001" />
          </div>
          <div class="form-group">
            <label for="acct-name">Name</label>
            <input id="acct-name" v-model.trim="newAccount.name" type="text" required placeholder="Revenue" />
          </div>
          <div class="form-group">
            <label for="acct-type">Type</label>
            <select id="acct-type" v-model="newAccount.type" required>
              <option value="">Select type</option>
              <option value="ASSET">Asset</option>
              <option value="LIABILITY">Liability</option>
              <option value="EQUITY">Equity</option>
              <option value="REVENUE">Revenue</option>
              <option value="EXPENSE">Expense</option>
            </select>
          </div>
          <div class="form-group">
            <label for="acct-desc">Description</label>
            <input id="acct-desc" v-model.trim="newAccount.description" type="text" placeholder="Optional" />
          </div>
          <button type="submit" class="btn-primary" :disabled="financeStore.loading">Create Account</button>
        </form>
      </section>

      <!-- Cost Centers Section -->
      <section class="section">
        <h2>Cost Centers</h2>
        <LoadingSpinner v-if="costCentersLoading" message="Loading cost centers..." />
        <div v-else-if="financeStore.costCenters.length === 0" class="empty-state">
          <p>No cost centers found. Create one below.</p>
        </div>
        <table v-else class="data-table">
          <thead>
            <tr>
              <th>Code</th>
              <th>Name</th>
              <th>Type</th>
              <th>Active</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="cc in financeStore.costCenters" :key="cc.id">
              <td class="mono">{{ cc.code }}</td>
              <td>{{ cc.name }}</td>
              <td>{{ cc.type || '--' }}</td>
              <td>
                <span class="badge" :class="cc.active !== false ? 'badge-green' : 'badge-gray'">
                  {{ cc.active !== false ? 'Active' : 'Inactive' }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>

        <h3>Create Cost Center</h3>
        <form class="inline-form" @submit.prevent="handleCreateCostCenter">
          <div class="form-group">
            <label for="cc-code">Code</label>
            <input id="cc-code" v-model.trim="newCostCenter.code" type="text" required placeholder="CC-100" />
          </div>
          <div class="form-group">
            <label for="cc-name">Name</label>
            <input id="cc-name" v-model.trim="newCostCenter.name" type="text" required placeholder="Marketing" />
          </div>
          <div class="form-group">
            <label for="cc-type">Type</label>
            <select id="cc-type" v-model="newCostCenter.type" required>
              <option value="">Select type</option>
              <option value="DEPARTMENT">Department</option>
              <option value="PROJECT">Project</option>
              <option value="LOCATION">Location</option>
            </select>
          </div>
          <div class="form-group">
            <label for="cc-desc">Description</label>
            <input id="cc-desc" v-model.trim="newCostCenter.description" type="text" placeholder="Optional" />
          </div>
          <button type="submit" class="btn-primary" :disabled="financeStore.loading">Create Cost Center</button>
        </form>
      </section>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useFinanceStore } from '@/stores/finance'
import { useAuthStore } from '@/stores/auth'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'
import apiClient from '@/api/client'

const financeStore = useFinanceStore()
const authStore = useAuthStore()

const periodsLoading = ref(false)
const accountsLoading = ref(false)
const costCentersLoading = ref(false)

const newAccount = ref({ code: '', name: '', type: '', description: '' })
const newCostCenter = ref({ code: '', name: '', type: '', description: '' })

async function loadAll() {
  periodsLoading.value = true
  accountsLoading.value = true
  costCentersLoading.value = true

  await financeStore.fetchPeriods()
  periodsLoading.value = false

  await financeStore.fetchAccounts()
  accountsLoading.value = false

  await financeStore.fetchCostCenters()
  costCentersLoading.value = false
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

async function handleCreateAccount() {
  const data = { ...newAccount.value }
  if (!data.description) delete data.description
  await financeStore.createAccount(data)
  if (!financeStore.error) {
    newAccount.value = { code: '', name: '', type: '', description: '' }
  }
}

async function handleCreateCostCenter() {
  const data = { ...newCostCenter.value }
  if (!data.description) delete data.description
  await financeStore.createCostCenter(data)
  if (!financeStore.error) {
    newCostCenter.value = { code: '', name: '', type: '', description: '' }
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
.accounting-periods-view {
  max-width: 1000px;
  margin: 0 auto;
  padding: 24px;
}

.accounting-periods-view h1 {
  margin: 0 0 20px;
  color: #1a237e;
}

.accounting-periods-view h2 {
  margin: 0 0 12px;
  color: #1a237e;
  font-size: 1.15rem;
}

.accounting-periods-view h3 {
  margin: 20px 0 10px;
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

.btn-close-period:hover:not(:disabled) { background: #fff8e1; }
.btn-close-period:disabled { opacity: 0.5; cursor: not-allowed; }

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

.btn-primary:hover:not(:disabled) { background: #283593; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }

.empty-state {
  text-align: center;
  padding: 32px 24px;
  color: #666;
}
</style>
