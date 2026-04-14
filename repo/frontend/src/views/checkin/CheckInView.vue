<template>
  <div class="check-in-view">
    <h1>Staff Check-In</h1>
    <p class="subtitle">Select a session and check in attendees</p>

    <OfflineIndicator />
    <PendingActionsBar />

    <ErrorAlert
      v-if="checkinStore.error"
      :message="checkinStore.error"
      title="Error"
      :retryable="true"
      :dismissable="true"
      @retry="reloadAll"
      @dismiss="checkinStore.error = null"
    />

    <!-- Session Selector -->
    <section class="section">
      <label for="session-select" class="section-label">Session</label>
      <div class="session-selector">
        <select
          id="session-select"
          v-model="selectedSessionId"
          :disabled="eventsLoading"
          class="session-dropdown"
        >
          <option value="">-- Select a session --</option>
          <option
            v-for="evt in eventsStore.events"
            :key="evt.id"
            :value="evt.id"
          >
            {{ evt.name || evt.title || evt.id }}
          </option>
        </select>
        <LoadingSpinner v-if="eventsLoading" message="Loading sessions..." />
      </div>
    </section>

    <template v-if="selectedSessionId">
      <!-- Passcode Display -->
      <section class="section passcode-section">
        <h2>Rotating Passcode</h2>
        <div class="passcode-display" v-if="checkinStore.currentPasscode">
          <span class="passcode-digits">{{ checkinStore.currentPasscode }}</span>
          <div class="countdown-bar">
            <div
              class="countdown-fill"
              :style="{ width: countdownPercent + '%' }"
            ></div>
          </div>
          <span class="countdown-text">
            Expires in {{ checkinStore.passcodeCountdown }}s
          </span>
        </div>
        <LoadingSpinner v-else message="Fetching passcode..." />
      </section>

      <!-- Check-In Form -->
      <section class="section">
        <h2>Check In Attendee</h2>

        <!-- Success feedback -->
        <div v-if="successMessage" class="feedback success-feedback">
          {{ successMessage }}
        </div>

        <!-- Conflict feedback -->
        <div v-if="conflictMessage" :class="['feedback', conflictSeverity]">
          {{ conflictMessage }}
          <div v-if="conflictWindowTimes" class="conflict-window-times">
            Window: {{ conflictWindowTimes }}
          </div>
          <button class="btn-dismiss" @click="dismissConflict">Dismiss</button>
        </div>

        <form @submit.prevent="handleCheckIn" class="checkin-form" novalidate>
          <div class="form-group">
            <label for="attendee-id">Attendee User ID</label>
            <input
              id="attendee-id"
              v-model.trim="attendeeUserId"
              type="text"
              placeholder="Enter attendee user ID"
              :disabled="checkinStore.loading"
              required
            />
          </div>

          <div class="form-group">
            <label for="passcode-input">Passcode</label>
            <input
              id="passcode-input"
              v-model="passcodeInput"
              type="text"
              maxlength="6"
              placeholder="Auto-filled from display"
              :disabled="checkinStore.loading"
            />
          </div>

          <div class="form-group">
            <label for="device-token">Device Token (optional)</label>
            <input
              id="device-token"
              v-model.trim="deviceToken"
              type="text"
              placeholder="e.g. browser fingerprint"
              :disabled="checkinStore.loading"
            />
          </div>

          <button
            type="submit"
            class="btn-primary"
            :disabled="checkinStore.loading || !attendeeUserId || !passcodeInput"
          >
            <span v-if="checkinStore.loading" class="btn-spinner"></span>
            {{ checkinStore.loading ? 'Checking in...' : 'Check In' }}
          </button>
        </form>
      </section>

      <!-- Roster Table -->
      <section class="section">
        <h2>Checked-In Roster</h2>
        <LoadingSpinner v-if="rosterLoading" message="Loading roster..." />
        <div v-else-if="checkinStore.roster.length === 0" class="empty-state">
          No attendees checked in yet for this session.
        </div>
        <div v-else class="table-wrapper">
          <table class="data-table">
            <thead>
              <tr>
                <th>User ID</th>
                <th>Name</th>
                <th>Checked In At</th>
                <th>Device</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="entry in checkinStore.roster" :key="entry.id || entry.userId">
                <td>{{ entry.userId }}</td>
                <td>{{ entry.userName || entry.name || '--' }}</td>
                <td>{{ formatTime(entry.checkedInAt || entry.timestamp) }}</td>
                <td>{{ entry.deviceToken || '--' }}</td>
                <td>
                  <span class="status-badge" :class="statusClass(entry.status)">
                    {{ entry.status || 'CHECKED_IN' }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- Conflicts Panel -->
      <section class="section" v-if="checkinStore.conflicts.length > 0">
        <h2>Device Conflict Records</h2>
        <div class="table-wrapper">
          <table class="data-table conflicts-table">
            <thead>
              <tr>
                <th>User ID</th>
                <th>Conflict Type</th>
                <th>Device Token</th>
                <th>Detected At</th>
                <th>Details</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="c in checkinStore.conflicts" :key="c.id">
                <td>{{ c.userId }}</td>
                <td>
                  <span class="conflict-badge">{{ c.conflictType || c.type }}</span>
                </td>
                <td>{{ c.deviceToken || '--' }}</td>
                <td>{{ formatTime(c.detectedAt || c.timestamp) }}</td>
                <td>{{ c.details || c.message || '--' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useCheckinStore } from '@/stores/checkin'
import { useEventsStore } from '@/stores/events'
import { useOfflineStore } from '@/stores/offline'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'
import OfflineIndicator from '@/components/offline/OfflineIndicator.vue'
import PendingActionsBar from '@/components/offline/PendingActionsBar.vue'

const checkinStore = useCheckinStore()
const eventsStore = useEventsStore()
const offlineStore = useOfflineStore()

const selectedSessionId = ref('')
const attendeeUserId = ref('')
const passcodeInput = ref('')
const deviceToken = ref('')
const successMessage = ref('')
const conflictMessage = ref('')
const conflictSeverity = ref('conflict-feedback')
const conflictWindowTimes = ref('')
const eventsLoading = ref(false)
const rosterLoading = ref(false)

let passcodeRefreshInterval = null
let successTimeout = null

const PASSCODE_MAX_SECONDS = 60

const countdownPercent = computed(() => {
  if (checkinStore.passcodeCountdown <= 0) return 0
  return (checkinStore.passcodeCountdown / PASSCODE_MAX_SECONDS) * 100
})

// Conflict code to user-friendly message mapping
const conflictMessages = {
  DUPLICATE_CHECKIN: 'This attendee has already been checked in',
  DEVICE_CONFLICT: 'Device conflict — attendee already bound to a different device today',
  CONCURRENT_DEVICE: 'Warning: Multiple devices detected for this attendee',
  WINDOW_CLOSED: 'Check-in window is not currently open',
  INVALID_PASSCODE: 'The passcode entered is invalid or expired',
  NOT_REGISTERED: 'This attendee is not registered for this session'
}

// Watch for session selection changes
watch(selectedSessionId, async (newId) => {
  if (!newId) {
    stopPasscodeRefresh()
    return
  }
  await loadSessionData(newId)
  startPasscodeRefresh(newId)
})

// Auto-fill passcode from display
watch(() => checkinStore.currentPasscode, (newPasscode) => {
  if (newPasscode) {
    passcodeInput.value = newPasscode
  }
})

onMounted(async () => {
  eventsLoading.value = true
  try {
    await eventsStore.fetchEvents({ size: 200 })
  } finally {
    eventsLoading.value = false
  }
})

onBeforeUnmount(() => {
  stopPasscodeRefresh()
  if (successTimeout) clearTimeout(successTimeout)
})

async function loadSessionData(sessionId) {
  rosterLoading.value = true
  try {
    // If offline, try cached roster
    if (!offlineStore.isOnline) {
      const cached = await offlineStore.getCachedRoster(sessionId)
      if (cached) {
        checkinStore.roster = cached
        rosterLoading.value = false
        return
      }
    }
    await Promise.all([
      checkinStore.fetchPasscode(sessionId),
      checkinStore.fetchRoster(sessionId),
      checkinStore.fetchConflicts(sessionId)
    ])
    // Cache roster for offline use
    if (checkinStore.roster.length > 0) {
      offlineStore.cacheRoster(sessionId, checkinStore.roster)
    }
  } finally {
    rosterLoading.value = false
  }
}

function startPasscodeRefresh(sessionId) {
  stopPasscodeRefresh()
  passcodeRefreshInterval = setInterval(() => {
    checkinStore.fetchPasscode(sessionId)
  }, 60000)
}

function stopPasscodeRefresh() {
  if (passcodeRefreshInterval) {
    clearInterval(passcodeRefreshInterval)
    passcodeRefreshInterval = null
  }
}

async function handleCheckIn() {
  if (!attendeeUserId.value || !passcodeInput.value || !selectedSessionId.value) return

  successMessage.value = ''
  conflictMessage.value = ''
  conflictWindowTimes.value = ''

  const payload = {
    userId: attendeeUserId.value,
    passcode: passcodeInput.value
  }
  if (deviceToken.value) {
    payload.deviceToken = deviceToken.value
  }

  // If offline, queue the action
  if (!offlineStore.isOnline) {
    await offlineStore.enqueuePendingAction({
      method: 'POST',
      url: `/checkin/sessions/${selectedSessionId.value}`,
      data: payload
    })
    successMessage.value = `Check-in for ${attendeeUserId.value} queued (offline). Will sync when back online.`
    attendeeUserId.value = ''
    autoHideSuccess()
    return
  }

  try {
    await checkinStore.performCheckIn(selectedSessionId.value, payload)
    successMessage.value = `Check-in successful for ${attendeeUserId.value}`
    attendeeUserId.value = ''
    autoHideSuccess()

    // Refresh roster and conflicts
    await Promise.all([
      checkinStore.fetchRoster(selectedSessionId.value),
      checkinStore.fetchConflicts(selectedSessionId.value)
    ])
    offlineStore.cacheRoster(selectedSessionId.value, checkinStore.roster)
  } catch (e) {
    const errData = e.response?.data
    const code = errData?.errors?.[0]?.code

    if (code && conflictMessages[code]) {
      conflictMessage.value = conflictMessages[code]

      if (code === 'WINDOW_CLOSED') {
        conflictSeverity.value = 'warning-feedback'
        const windowData = errData?.errors?.[0]?.meta || errData?.errors?.[0]
        if (windowData?.windowStart && windowData?.windowEnd) {
          conflictWindowTimes.value = `${windowData.windowStart} - ${windowData.windowEnd}`
        }
      } else if (code === 'CONCURRENT_DEVICE') {
        conflictSeverity.value = 'warning-feedback'
      } else {
        conflictSeverity.value = 'conflict-feedback'
      }
    } else if (checkinStore.conflictDetail) {
      const detail = checkinStore.conflictDetail
      conflictMessage.value = conflictMessages[detail.code] || detail.message
      conflictSeverity.value = detail.code === 'CONCURRENT_DEVICE' ? 'warning-feedback' : 'conflict-feedback'
    }
    // General errors are already handled by checkinStore.error
  }
}

function dismissConflict() {
  conflictMessage.value = ''
  conflictWindowTimes.value = ''
  checkinStore.clearConflict()
}

function autoHideSuccess() {
  if (successTimeout) clearTimeout(successTimeout)
  successTimeout = setTimeout(() => {
    successMessage.value = ''
  }, 5000)
}

function reloadAll() {
  checkinStore.error = null
  if (selectedSessionId.value) {
    loadSessionData(selectedSessionId.value)
  }
}

function formatTime(isoString) {
  if (!isoString) return '--'
  try {
    return new Date(isoString).toLocaleString()
  } catch {
    return isoString
  }
}

function statusClass(status) {
  if (!status) return 'status-success'
  const s = status.toUpperCase()
  if (s.includes('CHECKED_IN') || s.includes('SUCCESS')) return 'status-success'
  if (s.includes('DENIED') || s.includes('CONFLICT')) return 'status-error'
  return 'status-neutral'
}
</script>

<style scoped>
.check-in-view {
  max-width: 960px;
  margin: 0 auto;
  padding: 24px;
}

.check-in-view h1 {
  margin: 0 0 4px;
  font-size: 1.75rem;
  color: #1a237e;
}

.subtitle {
  margin: 0 0 20px;
  color: #666;
  font-size: 0.9rem;
}

.section {
  margin-bottom: 28px;
}

.section h2 {
  font-size: 1.15rem;
  color: #333;
  margin: 0 0 12px;
  padding-bottom: 6px;
  border-bottom: 1px solid #e0e0e0;
}

.section-label {
  display: block;
  font-weight: 600;
  font-size: 0.875rem;
  color: #333;
  margin-bottom: 6px;
}

/* Session selector */
.session-selector {
  display: flex;
  align-items: center;
  gap: 12px;
}

.session-dropdown {
  flex: 1;
  max-width: 420px;
  padding: 10px 12px;
  border: 1px solid #ccc;
  border-radius: 4px;
  font-size: 0.95rem;
  background: #fff;
}

.session-dropdown:focus {
  outline: none;
  border-color: #1a237e;
  box-shadow: 0 0 0 2px rgba(26, 35, 126, 0.15);
}

.session-dropdown:disabled {
  background: #f5f5f5;
  cursor: not-allowed;
}

/* Passcode display */
.passcode-section {
  background: #e8eaf6;
  border-radius: 8px;
  padding: 20px 24px;
  text-align: center;
}

.passcode-section h2 {
  border-bottom: none;
  color: #1a237e;
}

.passcode-display {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.passcode-digits {
  font-size: 3rem;
  font-weight: 700;
  letter-spacing: 0.4em;
  color: #1a237e;
  font-family: 'Courier New', Courier, monospace;
}

.countdown-bar {
  width: 200px;
  height: 6px;
  background: #c5cae9;
  border-radius: 3px;
  overflow: hidden;
}

.countdown-fill {
  height: 100%;
  background: #1a237e;
  border-radius: 3px;
  transition: width 1s linear;
}

.countdown-text {
  font-size: 0.85rem;
  color: #666;
}

/* Check-in form */
.checkin-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-width: 480px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.form-group label {
  font-weight: 600;
  font-size: 0.875rem;
  color: #333;
}

.form-group input {
  padding: 10px 12px;
  border: 1px solid #ccc;
  border-radius: 4px;
  font-size: 0.95rem;
  transition: border-color 0.2s;
}

.form-group input:focus {
  outline: none;
  border-color: #1a237e;
  box-shadow: 0 0 0 2px rgba(26, 35, 126, 0.15);
}

.form-group input:disabled {
  background: #f5f5f5;
  cursor: not-allowed;
}

.btn-primary {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px;
  background: #1a237e;
  color: #fff;
  border: none;
  border-radius: 4px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
  margin-top: 4px;
}

.btn-primary:hover:not(:disabled) {
  background: #283593;
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Feedback messages */
.feedback {
  padding: 12px 16px;
  border-radius: 4px;
  margin-bottom: 14px;
  font-size: 0.95rem;
}

.success-feedback {
  background: #e8f5e9;
  border: 1px solid #a5d6a7;
  color: #2e7d32;
}

.conflict-feedback {
  background: #ffebee;
  border: 1px solid #ef9a9a;
  color: #c62828;
}

.warning-feedback {
  background: #fff3e0;
  border: 1px solid #ffcc80;
  color: #e65100;
}

.conflict-window-times {
  margin-top: 6px;
  font-size: 0.85rem;
  font-style: italic;
}

.btn-dismiss {
  margin-top: 8px;
  padding: 4px 12px;
  border: 1px solid currentColor;
  background: transparent;
  color: inherit;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.85rem;
}

.btn-dismiss:hover {
  opacity: 0.8;
}

/* Empty state */
.empty-state {
  text-align: center;
  padding: 32px 16px;
  color: #999;
  font-size: 0.95rem;
}

/* Data tables */
.table-wrapper {
  overflow-x: auto;
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
  white-space: nowrap;
}

.data-table tbody tr:hover {
  background: #fafafa;
}

.status-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 0.8rem;
  font-weight: 600;
  text-transform: uppercase;
}

.status-success {
  background: #e8f5e9;
  color: #2e7d32;
}

.status-error {
  background: #ffebee;
  color: #c62828;
}

.status-neutral {
  background: #f5f5f5;
  color: #666;
}

/* Conflicts table */
.conflicts-table .conflict-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 0.8rem;
  font-weight: 600;
  background: #ffebee;
  color: #c62828;
}
</style>
