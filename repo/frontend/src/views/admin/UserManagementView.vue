<template>
  <div class="user-management-view">
    <h1>User Management</h1>

    <div v-if="!authStore.hasRole('SYSTEM_ADMIN')" class="access-denied">
      <h2>Access Denied</h2>
      <p>Only System Administrators can manage users.</p>
    </div>

    <template v-else>
      <LoadingSpinner v-if="adminStore.loading && !initialized" message="Loading users..." />

      <ErrorAlert
        v-if="adminStore.error"
        :message="adminStore.error"
        title="Error"
        :retryable="true"
        @retry="loadUsers"
        @dismiss="adminStore.clearMessages()"
      />

      <div v-if="adminStore.successMessage" class="success-alert" role="status">
        {{ adminStore.successMessage }}
        <button class="btn-dismiss" @click="adminStore.clearMessages()">Dismiss</button>
      </div>

      <template v-if="initialized">
        <!-- Create User Form -->
        <section class="section">
          <h2>Create New User</h2>
          <form class="inline-form" @submit.prevent="handleCreateUser">
            <div class="form-group">
              <label for="new-username">Username</label>
              <input id="new-username" v-model.trim="newUser.username" type="text" required placeholder="jsmith" />
            </div>
            <div class="form-group">
              <label for="new-password">Password</label>
              <input id="new-password" v-model="newUser.password" type="password" required placeholder="Min 8 chars" />
            </div>
            <div class="form-group">
              <label for="new-display">Display Name</label>
              <input id="new-display" v-model.trim="newUser.displayName" type="text" required placeholder="John Smith" />
            </div>
            <div class="form-group">
              <label for="new-role">Role</label>
              <select id="new-role" v-model="newUser.roleType" required>
                <option value="">Select role</option>
                <option v-for="role in roleOptions" :key="role" :value="role">
                  {{ formatLabel(role) }}
                </option>
              </select>
            </div>
            <button type="submit" class="btn-primary" :disabled="adminStore.loading">
              {{ adminStore.loading ? 'Creating...' : 'Create User' }}
            </button>
          </form>
        </section>

        <!-- Users Table -->
        <section class="section">
          <h2>Users</h2>
          <div v-if="adminStore.users.length === 0" class="empty-state">
            <p>No users found. Create one above.</p>
          </div>
          <table v-else class="data-table">
            <thead>
              <tr>
                <th>Username</th>
                <th>Display Name</th>
                <th>Role</th>
                <th>Status</th>
                <th>Last Login</th>
                <th>Created</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="user in adminStore.users" :key="user.id">
                <td class="mono">{{ user.username }}</td>
                <td>{{ user.displayName || '--' }}</td>
                <td>
                  <select
                    class="inline-select"
                    :value="user.roleType"
                    @change="handleRoleChange(user, $event)"
                    :disabled="adminStore.loading"
                  >
                    <option v-for="role in roleOptions" :key="role" :value="role">
                      {{ formatLabel(role) }}
                    </option>
                  </select>
                </td>
                <td>
                  <span class="badge" :class="userStatusClass(user.status)">
                    {{ user.status || 'ACTIVE' }}
                  </span>
                </td>
                <td>{{ formatDateTime(user.lastLoginAt) }}</td>
                <td>{{ formatDateTime(user.createdAt) }}</td>
                <td>
                  <button
                    v-if="(user.status || 'ACTIVE') === 'ACTIVE'"
                    class="btn-sm btn-disable"
                    :disabled="adminStore.loading"
                    @click="handleStatusToggle(user, 'DISABLED')"
                  >Disable</button>
                  <button
                    v-else
                    class="btn-sm btn-enable"
                    :disabled="adminStore.loading"
                    @click="handleStatusToggle(user, 'ACTIVE')"
                  >Enable</button>
                </td>
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
import { RoleType } from '@/api/types'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'

const adminStore = useAdminStore()
const authStore = useAuthStore()

const initialized = ref(false)

const roleOptions = Object.values(RoleType)

const newUser = ref({
  username: '',
  password: '',
  displayName: '',
  roleType: ''
})

async function loadUsers() {
  await adminStore.fetchUsers()
  initialized.value = true
}

async function handleCreateUser() {
  await adminStore.createUser({ ...newUser.value })
  if (!adminStore.error) {
    newUser.value = { username: '', password: '', displayName: '', roleType: '' }
  }
}

async function handleRoleChange(user, event) {
  const newRole = event.target.value
  if (newRole === user.roleType) return
  await adminStore.updateUser(user.id, { roleType: newRole })
}

async function handleStatusToggle(user, newStatus) {
  await adminStore.updateUser(user.id, { status: newStatus })
}

function formatLabel(val) {
  if (!val) return '--'
  return val.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())
}

function formatDateTime(iso) {
  if (!iso) return '--'
  try {
    return new Date(iso).toLocaleString(undefined, { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
  } catch { return iso }
}

function userStatusClass(status) {
  switch (status) {
    case 'ACTIVE': return 'badge-green'
    case 'LOCKED': return 'badge-red'
    case 'DISABLED': return 'badge-gray'
    default: return 'badge-green'
  }
}

onMounted(() => {
  if (authStore.hasRole('SYSTEM_ADMIN')) {
    loadUsers()
  }
})
</script>

<style scoped>
.user-management-view {
  max-width: 1100px;
  margin: 0 auto;
  padding: 24px;
}

.user-management-view h1 {
  margin: 0 0 20px;
  color: #1a237e;
}

.user-management-view h2 {
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
.form-group select {
  padding: 8px 10px;
  border: 1px solid #ccc;
  border-radius: 4px;
  font-size: 0.9rem;
}

.form-group input:focus,
.form-group select:focus {
  outline: none;
  border-color: #1a237e;
  box-shadow: 0 0 0 2px rgba(26, 35, 126, 0.15);
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85rem;
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

.inline-select {
  padding: 4px 8px;
  border: 1px solid #ccc;
  border-radius: 4px;
  font-size: 0.8rem;
  background: #fff;
  cursor: pointer;
}

.inline-select:focus {
  outline: none;
  border-color: #1a237e;
}

.inline-select:disabled {
  opacity: 0.5;
  cursor: not-allowed;
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
.badge-red { background: #ffebee; color: #c62828; }
.badge-gray { background: #f5f5f5; color: #757575; }

.btn-sm {
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid;
}

.btn-disable {
  background: #fff;
  border-color: #c62828;
  color: #c62828;
}

.btn-disable:hover:not(:disabled) { background: #ffebee; }

.btn-enable {
  background: #fff;
  border-color: #2e7d32;
  color: #2e7d32;
}

.btn-enable:hover:not(:disabled) { background: #e8f5e9; }

.btn-sm:disabled { opacity: 0.5; cursor: not-allowed; }

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
