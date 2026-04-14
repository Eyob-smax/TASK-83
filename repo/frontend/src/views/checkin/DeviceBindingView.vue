<template>
  <div class="device-binding-view">
    <h1>Device Binding Status</h1>
    <p class="subtitle">
      Monitor device bindings and multi-device conflict warnings for today's sessions
    </p>

    <ErrorAlert
      v-if="error"
      :message="error"
      title="Error"
      :retryable="true"
      :dismissable="true"
      @retry="loadData"
      @dismiss="error = null"
    />

    <!-- Anti-Proxy Explanation -->
    <section class="section info-panel">
      <h2>About Device Binding</h2>
      <p>
        Device binding is an anti-proxy measure that ties each attendee to a single
        physical device per check-in day. When enabled for a session, the first device
        used to check in becomes the attendee's bound device. Subsequent check-in
        attempts from a different device will be flagged as a
        <strong>DEVICE_CONFLICT</strong>.
      </p>
      <p>
        If the system detects simultaneous check-in activity from multiple devices for
        the same user, a <strong>CONCURRENT_DEVICE</strong> warning is raised. These
        warnings do not block check-in but are logged for review.
      </p>
    </section>

    <!-- Session Selector -->
    <section class="section">
      <label for="session-select" class="section-label">Select Session</label>
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
      <!-- Binding Status Summary -->
      <section class="section">
        <h2>Binding Requirement</h2>
        <LoadingSpinner v-if="loading" message="Loading binding status..." />
        <div v-else class="binding-status-card">
          <div class="status-row">
            <span class="status-label">Device binding required:</span>
            <span :class="['status-value', bindingRequired ? 'active' : 'inactive']">
              {{ bindingRequired ? 'Yes' : 'No' }}
            </span>
          </div>
          <div class="status-row">
            <span class="status-label">Devices bound today:</span>
            <span class="status-value">{{ boundDevices.length }}</span>
          </div>
          <div class="status-row">
            <span class="status-label">Active conflicts:</span>
            <span :class="['status-value', conflictCount > 0 ? 'conflict' : '']">
              {{ conflictCount }}
            </span>
          </div>
        </div>
      </section>

      <!-- Bound Devices List -->
      <section class="section">
        <h2>Today's Bound Devices</h2>
        <LoadingSpinner v-if="loading" message="Loading devices..." />
        <div v-else-if="boundDevices.length === 0" class="empty-state">
          No device bindings recorded for today.
        </div>
        <div v-else class="table-wrapper">
          <table class="data-table">
            <thead>
              <tr>
                <th>User ID</th>
                <th>User Name</th>
                <th>Device Token</th>
                <th>Bound At</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="device in boundDevices" :key="device.id || device.userId + device.deviceToken">
                <td>{{ device.userId }}</td>
                <td>{{ device.userName || device.name || '--' }}</td>
                <td class="mono-text">{{ device.deviceToken || '--' }}</td>
                <td>{{ formatTime(device.boundAt || device.checkedInAt || device.timestamp) }}</td>
                <td>
                  <span
                    class="status-badge"
                    :class="device.hasConflict ? 'status-error' : 'status-success'"
                  >
                    {{ device.hasConflict ? 'CONFLICT' : 'BOUND' }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- Multi-Device Conflict Warnings -->
      <section class="section" v-if="multiDeviceUsers.length > 0">
        <h2>Multi-Device Conflict Warnings</h2>
        <div class="warning-list">
          <div
            v-for="user in multiDeviceUsers"
            :key="user.userId"
            class="warning-card"
          >
            <div class="warning-icon">!</div>
            <div class="warning-content">
              <strong>{{ user.userId }}</strong>
              <span v-if="user.userName"> ({{ user.userName }})</span>
              <p>
                This user has {{ user.deviceCount }} devices bound today.
                Devices: {{ user.devices.join(', ') }}
              </p>
              <span class="warning-time">
                First detected: {{ formatTime(user.firstDetected) }}
              </span>
            </div>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useCheckinStore } from '@/stores/checkin'
import { useEventsStore } from '@/stores/events'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'

const checkinStore = useCheckinStore()
const eventsStore = useEventsStore()

const selectedSessionId = ref('')
const eventsLoading = ref(false)
const loading = ref(false)
const error = ref(null)
const bindingRequired = ref(false)

// Derived from roster and conflicts data
const boundDevices = computed(() => {
  // Build from roster entries that have device tokens
  const entries = checkinStore.roster
    .filter(entry => entry.deviceToken)
    .map(entry => {
      const hasConflict = checkinStore.conflicts.some(
        c => c.userId === entry.userId
      )
      return { ...entry, hasConflict }
    })
  return entries
})

const conflictCount = computed(() => checkinStore.conflicts.length)

const multiDeviceUsers = computed(() => {
  // Group conflicts by user to find multi-device users
  const userMap = {}
  for (const c of checkinStore.conflicts) {
    const type = c.conflictType || c.type || ''
    if (type === 'CONCURRENT_DEVICE' || type === 'DEVICE_CONFLICT') {
      if (!userMap[c.userId]) {
        userMap[c.userId] = {
          userId: c.userId,
          userName: c.userName || c.name || '',
          devices: new Set(),
          firstDetected: c.detectedAt || c.timestamp,
          deviceCount: 0
        }
      }
      if (c.deviceToken) {
        userMap[c.userId].devices.add(c.deviceToken)
      }
      // Track earliest detection
      const ts = c.detectedAt || c.timestamp
      if (ts && ts < userMap[c.userId].firstDetected) {
        userMap[c.userId].firstDetected = ts
      }
    }
  }

  return Object.values(userMap).map(u => ({
    ...u,
    devices: Array.from(u.devices),
    deviceCount: u.devices.size || 1
  })).filter(u => u.deviceCount > 1 || checkinStore.conflicts.filter(c => c.userId === u.userId).length > 1)
})

watch(selectedSessionId, async (newId) => {
  if (newId) {
    await loadData()
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

async function loadData() {
  if (!selectedSessionId.value) return
  loading.value = true
  error.value = null
  try {
    await Promise.all([
      checkinStore.fetchRoster(selectedSessionId.value),
      checkinStore.fetchConflicts(selectedSessionId.value)
    ])

    // Determine binding requirement from event metadata
    const evt = eventsStore.events.find(e => e.id === selectedSessionId.value)
    bindingRequired.value = evt?.deviceBindingRequired ?? evt?.requireDeviceBinding ?? true
  } catch (e) {
    error.value = e.response?.data?.message || 'Failed to load device binding data'
  } finally {
    loading.value = false
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
</script>

<style scoped>
.device-binding-view {
  max-width: 960px;
  margin: 0 auto;
  padding: 24px;
}

.device-binding-view h1 {
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

/* Info panel */
.info-panel {
  background: #f3e5f5;
  border: 1px solid #ce93d8;
  border-radius: 6px;
  padding: 16px 20px;
}

.info-panel h2 {
  border-bottom: none;
  color: #6a1b9a;
  margin-bottom: 8px;
}

.info-panel p {
  margin: 0 0 8px;
  color: #4a148c;
  font-size: 0.9rem;
  line-height: 1.5;
}

.info-panel p:last-child {
  margin-bottom: 0;
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

/* Binding status card */
.binding-status-card {
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 6px;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 400px;
}

.status-label {
  font-size: 0.9rem;
  color: #555;
}

.status-value {
  font-weight: 600;
  font-size: 0.95rem;
  color: #333;
}

.status-value.active {
  color: #2e7d32;
}

.status-value.inactive {
  color: #999;
}

.status-value.conflict {
  color: #c62828;
}

/* Empty state */
.empty-state {
  text-align: center;
  padding: 32px 16px;
  color: #999;
  font-size: 0.95rem;
}

/* Table */
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

.mono-text {
  font-family: 'Courier New', Courier, monospace;
  font-size: 0.85rem;
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

/* Warning cards */
.warning-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.warning-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  background: #fff3e0;
  border: 1px solid #ffcc80;
  border-radius: 6px;
  padding: 14px 16px;
}

.warning-icon {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  background: #e65100;
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 1rem;
}

.warning-content {
  flex: 1;
}

.warning-content strong {
  color: #e65100;
}

.warning-content p {
  margin: 4px 0 6px;
  font-size: 0.9rem;
  color: #bf360c;
  line-height: 1.4;
}

.warning-time {
  font-size: 0.8rem;
  color: #999;
}
</style>
