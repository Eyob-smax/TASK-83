<template>
  <div class="allocation-view">
    <h1>Allocations</h1>

    <div v-if="!authStore.hasAnyRole(['FINANCE_MANAGER', 'SYSTEM_ADMIN'])" class="access-denied">
      <h2>Access Denied</h2>
      <p>You do not have permission to manage allocations.</p>
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

      <!-- Allocation Rules Section -->
      <section class="section">
        <h2>Allocation Rules</h2>
        <LoadingSpinner v-if="rulesLoading" message="Loading rules..." />
        <div v-else-if="financeStore.rules.length === 0" class="empty-state">
          <p>No allocation rules found. Create one below.</p>
        </div>
        <table v-else class="data-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Method</th>
              <th>Recognition</th>
              <th>Version</th>
              <th>Active</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="rule in financeStore.rules" :key="rule.id">
              <td>{{ rule.name }}</td>
              <td>
                <span class="badge badge-method">{{ rule.allocationMethod }}</span>
              </td>
              <td>{{ formatLabel(rule.recognitionMethod) }}</td>
              <td>v{{ rule.version || 1 }}</td>
              <td>
                <span class="badge" :class="rule.active !== false ? 'badge-green' : 'badge-gray'">
                  {{ rule.active !== false ? 'Active' : 'Inactive' }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>

        <h3>Create Allocation Rule</h3>
        <form class="stacked-form" @submit.prevent="handleCreateRule">
          <div class="form-row">
            <div class="form-group">
              <label for="rule-name">Name</label>
              <input id="rule-name" v-model.trim="newRule.name" type="text" required placeholder="Revenue Split Rule" />
            </div>
            <div class="form-group">
              <label for="rule-method">Allocation Method</label>
              <select id="rule-method" v-model="newRule.allocationMethod" required>
                <option value="">Select method</option>
                <option value="PROPORTIONAL">Proportional</option>
                <option value="FIXED">Fixed</option>
                <option value="TIERED">Tiered</option>
              </select>
            </div>
            <div class="form-group">
              <label for="rule-recognition">Recognition Method</label>
              <select id="rule-recognition" v-model="newRule.recognitionMethod" required>
                <option value="">Select method</option>
                <option value="IMMEDIATE">Immediate</option>
                <option value="OVER_SESSION_DATES">Over Session Dates</option>
              </select>
            </div>
          </div>
          <div class="form-group full-width">
            <label for="rule-config">Rule Configuration (JSON)</label>
            <textarea
              id="rule-config"
              v-model="newRule.ruleConfig"
              rows="4"
              placeholder='{"splits": [{"costCenter": "CC-100", "percentage": 50}]}'
            ></textarea>
          </div>
          <button type="submit" class="btn-primary" :disabled="financeStore.loading">Create Rule</button>
        </form>
      </section>

      <!-- Execute Posting Section -->
      <section class="section">
        <h2>Execute Posting</h2>
        <form class="stacked-form" @submit.prevent="handleExecutePosting">
          <div class="form-row">
            <div class="form-group">
              <label for="post-period">Period</label>
              <select id="post-period" v-model="postingForm.periodId" required>
                <option value="">Select period</option>
                <option
                  v-for="period in openPeriods"
                  :key="period.id"
                  :value="period.id"
                >{{ period.name }}</option>
              </select>
            </div>
            <div class="form-group">
              <label for="post-rule">Rule</label>
              <select id="post-rule" v-model="postingForm.ruleId" required>
                <option value="">Select rule</option>
                <option
                  v-for="rule in activeRules"
                  :key="rule.id"
                  :value="rule.id"
                >{{ rule.name }} (v{{ rule.version || 1 }})</option>
              </select>
            </div>
            <div class="form-group">
              <label for="post-amount">Total Amount</label>
              <input id="post-amount" v-model.number="postingForm.totalAmount" type="number" step="0.01" min="0" required placeholder="10000.00" />
            </div>
          </div>
          <div class="form-group full-width">
            <label for="post-desc">Description</label>
            <input id="post-desc" v-model.trim="postingForm.description" type="text" placeholder="Q1 Revenue allocation" />
          </div>
          <button type="submit" class="btn-primary" :disabled="financeStore.loading">
            {{ financeStore.loading ? 'Posting...' : 'Execute Posting' }}
          </button>
        </form>

        <div v-if="lastPostingResult" class="posting-result" :class="lastPostingResult.error ? 'result-error' : 'result-success'">
          <template v-if="!lastPostingResult.error">
            <strong>Posting Successful</strong>
            <p>Posting ID: <span class="mono">{{ lastPostingResult.id }}</span></p>
          </template>
          <template v-else>
            <strong>Posting Failed</strong>
            <p>{{ lastPostingResult.message }}</p>
          </template>
        </div>
      </section>

      <!-- Posting History Section -->
      <section class="section">
        <h2>Posting History</h2>
        <div class="form-row" style="margin-bottom: 12px;">
          <div class="form-group">
            <label for="history-period">Filter by Period</label>
            <select id="history-period" v-model="historyPeriodId" @change="loadPostings">
              <option value="">All periods</option>
              <option
                v-for="period in financeStore.periods"
                :key="period.id"
                :value="period.id"
              >{{ period.name }}</option>
            </select>
          </div>
        </div>

        <LoadingSpinner v-if="postingsLoading" message="Loading postings..." />
        <div v-else-if="financeStore.postings.length === 0" class="empty-state">
          <p>No postings found.</p>
        </div>
        <table v-else class="data-table">
          <thead>
            <tr>
              <th>Period</th>
              <th>Rule</th>
              <th>Amount</th>
              <th>Status</th>
              <th>Posted At</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="posting in financeStore.postings"
              :key="posting.id"
              :class="{ 'row-selected': selectedPosting?.id === posting.id }"
            >
              <td>{{ periodName(posting.periodId) }}</td>
              <td>{{ ruleName(posting.ruleId) }}</td>
              <td class="mono">${{ formatAmount(posting.totalAmount) }}</td>
              <td>
                <span class="badge" :class="postingStatusClass(posting.status)">
                  {{ posting.status }}
                </span>
              </td>
              <td>{{ formatDateTime(posting.postedAt || posting.createdAt) }}</td>
              <td>
                <button class="btn-sm btn-detail" @click="viewLineItems(posting)">Line Items</button>
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <!-- Line Items Panel -->
      <section v-if="selectedPosting" class="section line-items-panel">
        <div class="panel-header">
          <h2>Line Items - Posting {{ selectedPosting.id }}</h2>
          <button class="btn-sm btn-close" @click="selectedPosting = null">Close</button>
        </div>
        <LoadingSpinner v-if="lineItemsLoading" message="Loading line items..." />
        <div v-else-if="financeStore.currentLineItems.length === 0" class="empty-state">
          <p>No line items found for this posting.</p>
        </div>
        <table v-else class="data-table">
          <thead>
            <tr>
              <th>Account</th>
              <th>Cost Center</th>
              <th>Amount</th>
              <th>Recognition Start</th>
              <th>Recognition End</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in financeStore.currentLineItems" :key="item.id">
              <td class="mono">{{ item.accountCode || item.accountId }}</td>
              <td class="mono">{{ item.costCenterCode || item.costCenterId }}</td>
              <td class="mono">${{ formatAmount(item.amount) }}</td>
              <td>{{ formatDate(item.recognitionStartDate) }}</td>
              <td>{{ formatDate(item.recognitionEndDate) }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useFinanceStore } from '@/stores/finance'
import { useAuthStore } from '@/stores/auth'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'

const financeStore = useFinanceStore()
const authStore = useAuthStore()

const rulesLoading = ref(false)
const postingsLoading = ref(false)
const lineItemsLoading = ref(false)

const newRule = ref({ name: '', allocationMethod: '', recognitionMethod: '', ruleConfig: '' })
const postingForm = ref({ periodId: '', ruleId: '', totalAmount: null, description: '' })
const historyPeriodId = ref('')
const selectedPosting = ref(null)
const lastPostingResult = ref(null)

const openPeriods = computed(() =>
  financeStore.periods.filter(p => p.status === 'OPEN')
)

const activeRules = computed(() =>
  financeStore.rules.filter(r => r.active !== false)
)

async function loadAll() {
  rulesLoading.value = true
  await Promise.all([
    financeStore.fetchPeriods(),
    financeStore.fetchRules()
  ])
  rulesLoading.value = false
  await loadPostings()
}

async function loadPostings() {
  postingsLoading.value = true
  const params = historyPeriodId.value ? historyPeriodId.value : undefined
  await financeStore.fetchPostings(params)
  postingsLoading.value = false
}

async function handleCreateRule() {
  const data = { ...newRule.value }
  if (data.ruleConfig) {
    try {
      data.ruleConfig = JSON.parse(data.ruleConfig)
    } catch {
      financeStore.error = 'Invalid JSON in rule configuration'
      return
    }
  } else {
    delete data.ruleConfig
  }
  await financeStore.createRule(data)
  if (!financeStore.error) {
    newRule.value = { name: '', allocationMethod: '', recognitionMethod: '', ruleConfig: '' }
  }
}

async function handleExecutePosting() {
  lastPostingResult.value = null
  try {
    const result = await financeStore.executePosting({
      periodId: postingForm.value.periodId,
      ruleId: postingForm.value.ruleId,
      totalAmount: postingForm.value.totalAmount,
      description: postingForm.value.description || undefined
    })
    lastPostingResult.value = { id: result.id, error: false }
    postingForm.value = { periodId: '', ruleId: '', totalAmount: null, description: '' }
  } catch (e) {
    lastPostingResult.value = { error: true, message: financeStore.error }
  }
}

async function viewLineItems(posting) {
  selectedPosting.value = posting
  lineItemsLoading.value = true
  await financeStore.fetchLineItems(posting.id)
  lineItemsLoading.value = false
}

function periodName(id) {
  const p = financeStore.periods.find(p => p.id === id)
  return p ? p.name : id || '--'
}

function ruleName(id) {
  const r = financeStore.rules.find(r => r.id === id)
  return r ? r.name : id || '--'
}

function formatLabel(val) {
  if (!val) return '--'
  return val.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())
}

function formatAmount(val) {
  if (val == null) return '0.00'
  return Number(val).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatDate(iso) {
  if (!iso) return '--'
  try {
    return new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })
  } catch { return iso }
}

function formatDateTime(iso) {
  if (!iso) return '--'
  try {
    return new Date(iso).toLocaleString(undefined, { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
  } catch { return iso }
}

function postingStatusClass(status) {
  switch (status) {
    case 'POSTED': return 'badge-green'
    case 'REVERSED': return 'badge-red'
    case 'DRAFT': return 'badge-gray'
    case 'FAILED': return 'badge-red'
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
.allocation-view {
  max-width: 1100px;
  margin: 0 auto;
  padding: 24px;
}

.allocation-view h1 {
  margin: 0 0 20px;
  color: #1a237e;
}

.allocation-view h2 {
  margin: 0 0 12px;
  color: #1a237e;
  font-size: 1.15rem;
}

.allocation-view h3 {
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

.row-selected {
  background: #e8eaf6;
}

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
.badge-method { background: #e3f2fd; color: #1565c0; }

.btn-sm {
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid;
}

.btn-detail {
  background: #fff;
  border-color: #1a237e;
  color: #1a237e;
}

.btn-detail:hover { background: #e8eaf6; }

.btn-close {
  background: #fff;
  border-color: #999;
  color: #666;
}

.btn-close:hover { background: #f5f5f5; }

.stacked-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.form-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 180px;
}

.form-group.full-width {
  width: 100%;
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
  font-family: inherit;
}

.form-group textarea {
  font-family: monospace;
  resize: vertical;
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
  align-self: flex-start;
  transition: background 0.2s;
}

.btn-primary:hover:not(:disabled) { background: #283593; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }

.posting-result {
  margin-top: 16px;
  padding: 12px 16px;
  border-radius: 4px;
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

.posting-result p {
  margin: 4px 0 0;
}

.line-items-panel {
  border-color: #1a237e;
  border-width: 2px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.panel-header h2 {
  margin: 0;
}

.empty-state {
  text-align: center;
  padding: 32px 24px;
  color: #666;
}
</style>
