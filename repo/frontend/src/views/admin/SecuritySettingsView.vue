<template>
  <div class="security-settings-view">
    <h1>Security Settings</h1>

    <div v-if="!authStore.hasRole('SYSTEM_ADMIN')" class="access-denied">
      <h2>Access Denied</h2>
      <p>Only System Administrators can view security settings.</p>
    </div>

    <template v-else>
      <LoadingSpinner v-if="adminStore.loading && !initialized" message="Loading security settings..." />

      <ErrorAlert
        v-if="adminStore.error"
        :message="adminStore.error"
        title="Error"
        :retryable="true"
        @retry="loadSettings"
        @dismiss="adminStore.clearMessages()"
      />

      <template v-if="initialized">
        <!-- Roles Overview -->
        <section class="section">
          <h2>Roles Overview</h2>
          <table class="data-table">
            <thead>
              <tr>
                <th>Role</th>
                <th>Description</th>
                <th>Scope</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td><span class="badge badge-role">ATTENDEE</span></td>
                <td>Event attendees who register for sessions and check in</td>
                <td>Registration, check-in, personal notifications</td>
              </tr>
              <tr>
                <td><span class="badge badge-role">EVENT_STAFF</span></td>
                <td>Staff members who manage events and handle check-ins</td>
                <td>Event management, check-in operations, imports</td>
              </tr>
              <tr>
                <td><span class="badge badge-role">FINANCE_MANAGER</span></td>
                <td>Finance team members managing accounting and allocations</td>
                <td>Accounting periods, cost centers, allocation rules, postings</td>
              </tr>
              <tr>
                <td><span class="badge badge-role">SYSTEM_ADMIN</span></td>
                <td>Administrators with full system access</td>
                <td>All operations, user management, audit, backups, security</td>
              </tr>
            </tbody>
          </table>
        </section>

        <!-- Rate Limiting -->
        <section class="section">
          <h2>Rate Limiting</h2>
          <div class="config-grid">
            <div class="config-item">
              <span class="config-label">API Rate Limit</span>
              <span class="config-value">
                {{ settings?.rateLimitPerMinute || 60 }} requests / minute
              </span>
            </div>
            <div class="config-item">
              <span class="config-label">Login Throttle - Max Attempts</span>
              <span class="config-value">
                {{ settings?.loginMaxAttempts || 5 }} attempts
              </span>
            </div>
            <div class="config-item">
              <span class="config-label">Login Throttle - Lockout Duration</span>
              <span class="config-value">
                {{ settings?.loginLockoutMinutes || 15 }} minutes
              </span>
            </div>
            <div class="config-item">
              <span class="config-label">Status</span>
              <span class="config-value">
                <span class="badge badge-green">Active</span>
              </span>
            </div>
          </div>
        </section>

        <!-- Signature Verification -->
        <section class="section">
          <h2>Signature Verification</h2>
          <div class="config-grid">
            <div class="config-item">
              <span class="config-label">Algorithm</span>
              <span class="config-value mono">HMAC-SHA256</span>
            </div>
            <div class="config-item">
              <span class="config-label">Max Signature Age</span>
              <span class="config-value">
                {{ settings?.signatureMaxAgeSeconds || 300 }} seconds
              </span>
            </div>
            <div class="config-item">
              <span class="config-label">Status</span>
              <span class="config-value">
                <span class="badge badge-green">Enabled</span>
              </span>
            </div>
          </div>
        </section>

        <!-- Encryption -->
        <section class="section">
          <h2>Encryption</h2>
          <div class="config-grid">
            <div class="config-item">
              <span class="config-label">Algorithm</span>
              <span class="config-value mono">AES-256-GCM</span>
            </div>
            <div class="config-item">
              <span class="config-label">PII Display Default</span>
              <span class="config-value">
                {{ settings?.piiDisplayMode || 'Masked' }}
              </span>
            </div>
            <div class="config-item">
              <span class="config-label">Status</span>
              <span class="config-value">
                <span class="badge badge-green">Active</span>
              </span>
            </div>
          </div>
        </section>

        <p class="footer-note">
          These settings reflect the current backend security configuration. Changes are managed through environment configuration.
        </p>
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

const settings = computed(() => adminStore.securitySettings)

async function loadSettings() {
  await adminStore.fetchSecuritySettings()
  initialized.value = true
}

onMounted(() => {
  if (authStore.hasRole('SYSTEM_ADMIN')) {
    loadSettings()
  }
})
</script>

<style scoped>
.security-settings-view {
  max-width: 900px;
  margin: 0 auto;
  padding: 24px;
}

.security-settings-view h1 {
  margin: 0 0 20px;
  color: #1a237e;
}

.security-settings-view h2 {
  margin: 0 0 16px;
  color: #1a237e;
  font-size: 1.15rem;
}

.access-denied {
  text-align: center;
  padding: 60px 24px;
  color: #c62828;
}

.access-denied h2 { color: #c62828; }

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

.config-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 16px;
}

.config-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  background: #fafafa;
  border-radius: 6px;
  border: 1px solid #eee;
}

.config-label {
  font-size: 0.8rem;
  font-weight: 600;
  color: #555;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.config-value {
  font-size: 1rem;
  color: #222;
  font-weight: 500;
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
.badge-role { background: #e8eaf6; color: #1a237e; }

.footer-note {
  color: #888;
  font-size: 0.85rem;
  font-style: italic;
  margin-top: 8px;
}
</style>
