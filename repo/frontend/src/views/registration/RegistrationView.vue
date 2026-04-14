<template>
  <div class="registration-view">
    <h1>My Registrations</h1>

    <LoadingSpinner v-if="registrationStore.loading && registrations.length === 0" message="Loading registrations..." />

    <ErrorAlert
      v-else-if="registrationStore.error"
      :message="registrationStore.error"
      title="Failed to Load"
      :retryable="true"
      @retry="loadRegistrations"
      @dismiss="registrationStore.error = null"
    />

    <div v-else-if="registrations.length === 0" class="empty-state">
      <div class="empty-icon">&#128203;</div>
      <h2>No registrations yet</h2>
      <p>You haven't registered for any sessions. Browse available sessions to get started.</p>
      <router-link to="/" class="btn-browse">Browse Sessions</router-link>
    </div>

    <div v-else class="registration-list">
      <div
        v-for="reg in registrations"
        :key="reg.id"
        class="reg-card"
      >
        <div class="reg-info">
          <h3 class="reg-title">{{ reg.sessionTitle || reg.eventTitle || 'Session #' + reg.sessionId }}</h3>
          <span class="status-badge" :class="statusClass(reg.status)">
            {{ reg.status }}
          </span>
        </div>
        <div class="reg-meta">
          <span v-if="reg.registeredAt" class="meta-item">
            Registered: {{ formatDateTime(reg.registeredAt) }}
          </span>
          <span v-if="reg.sessionStartTime" class="meta-item">
            Session: {{ formatDateTime(reg.sessionStartTime) }}
          </span>
        </div>
        <div class="reg-actions">
          <button
            v-if="isActive(reg.status)"
            class="btn-cancel"
            :disabled="registrationStore.loading"
            @click="promptCancel(reg)"
          >Cancel Registration</button>
          <router-link :to="'/events/' + reg.sessionId" class="btn-view">View Session</router-link>
        </div>
      </div>
    </div>

    <ConfirmDialog
      :visible="showConfirm"
      :message="confirmMessage"
      @confirm="confirmCancel"
      @cancel="showConfirm = false"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRegistrationStore } from '@/stores/registration'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'

const registrationStore = useRegistrationStore()

const showConfirm = ref(false)
const confirmMessage = ref('')
const pendingCancelId = ref(null)

const registrations = computed(() => registrationStore.registrations)

function loadRegistrations() {
  registrationStore.fetchRegistrations()
}

function statusClass(status) {
  switch (status) {
    case 'CONFIRMED': return 'badge-confirmed'
    case 'WAITLISTED': return 'badge-waitlisted'
    case 'PROMOTED': return 'badge-promoted'
    case 'CANCELLED': return 'badge-cancelled'
    case 'EXPIRED': return 'badge-cancelled'
    default: return ''
  }
}

function isActive(status) {
  return status === 'CONFIRMED' || status === 'WAITLISTED' || status === 'PROMOTED'
}

function promptCancel(reg) {
  pendingCancelId.value = reg.id
  const title = reg.sessionTitle || reg.eventTitle || 'this session'
  confirmMessage.value = `Are you sure you want to cancel your registration for "${title}"? This action cannot be undone.`
  showConfirm.value = true
}

async function confirmCancel() {
  showConfirm.value = false
  if (pendingCancelId.value) {
    await registrationStore.cancelReg(pendingCancelId.value)
    pendingCancelId.value = null
  }
}

function formatDateTime(iso) {
  if (!iso) return '—'
  try {
    const d = new Date(iso)
    return d.toLocaleString(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    })
  } catch {
    return iso
  }
}

onMounted(() => {
  loadRegistrations()
})
</script>

<style scoped>
.registration-view {
  max-width: 800px;
  margin: 0 auto;
  padding: 24px;
}

.registration-view h1 {
  margin: 0 0 20px;
  color: #1a237e;
}

.empty-state {
  text-align: center;
  padding: 60px 24px;
  color: #666;
}

.empty-icon {
  font-size: 3rem;
  margin-bottom: 12px;
}

.empty-state h2 {
  margin: 0 0 8px;
  color: #333;
}

.btn-browse {
  display: inline-block;
  margin-top: 16px;
  padding: 10px 20px;
  background: #1a237e;
  color: #fff;
  border-radius: 4px;
  text-decoration: none;
  font-weight: 600;
  font-size: 0.9rem;
}

.btn-browse:hover {
  background: #283593;
}

.registration-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.reg-card {
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 20px;
}

.reg-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.reg-title {
  margin: 0;
  font-size: 1.05rem;
  color: #333;
}

.status-badge {
  padding: 3px 12px;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 700;
  text-transform: uppercase;
  white-space: nowrap;
}

.badge-confirmed { background: #e8f5e9; color: #2e7d32; }
.badge-waitlisted { background: #fff8e1; color: #f57f17; }
.badge-promoted { background: #e3f2fd; color: #1565c0; }
.badge-cancelled { background: #f5f5f5; color: #9e9e9e; }

.reg-meta {
  display: flex;
  gap: 20px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.meta-item {
  font-size: 0.825rem;
  color: #777;
}

.reg-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.btn-cancel {
  padding: 6px 14px;
  border: 1px solid #c62828;
  background: #fff;
  color: #c62828;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.825rem;
  font-weight: 600;
  transition: background 0.2s;
}

.btn-cancel:hover:not(:disabled) {
  background: #ffebee;
}

.btn-cancel:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-view {
  padding: 6px 14px;
  border: 1px solid #1a237e;
  background: #fff;
  color: #1a237e;
  border-radius: 4px;
  text-decoration: none;
  font-size: 0.825rem;
  font-weight: 600;
  transition: background 0.2s;
}

.btn-view:hover {
  background: #e8eaf6;
}
</style>
