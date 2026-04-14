<template>
  <div class="login-view">
    <div class="login-card">
      <h1>Sign In</h1>
      <p class="subtitle">Enter your credentials to access EventOps</p>

      <ErrorAlert
        v-if="errorMessage"
        :message="errorMessage"
        :title="errorTitle"
        :dismissable="true"
        @dismiss="clearError"
      />

      <form @submit.prevent="handleLogin" class="login-form" novalidate>
        <div class="form-group">
          <label for="username">Username</label>
          <input
            id="username"
            v-model.trim="username"
            type="text"
            autocomplete="username"
            placeholder="Enter your username"
            :disabled="submitting"
            required
          />
        </div>

        <div class="form-group">
          <label for="password">Password</label>
          <input
            id="password"
            v-model="password"
            type="password"
            autocomplete="current-password"
            placeholder="Enter your password"
            :disabled="submitting"
            required
          />
        </div>

        <button type="submit" class="btn-primary" :disabled="submitting || !isFormValid">
          <span v-if="submitting" class="btn-spinner"></span>
          {{ submitting ? 'Signing in...' : 'Sign In' }}
        </button>
      </form>

      <p class="account-link">
        Don't have an account?
        <router-link to="/account">Create one</router-link>
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import ErrorAlert from '@/components/common/ErrorAlert.vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const username = ref('')
const password = ref('')
const submitting = ref(false)
const errorMessage = ref('')
const errorTitle = ref('Error')

const isFormValid = computed(() => username.value.length > 0 && password.value.length > 0)

function clearError() {
  errorMessage.value = ''
  errorTitle.value = 'Error'
}

async function handleLogin() {
  if (!isFormValid.value || submitting.value) return

  clearError()
  submitting.value = true

  try {
    await authStore.login({ username: username.value, password: password.value })
    const redirectPath = route.query.redirect || '/'
    router.push(redirectPath)
  } catch (err) {
    const status = err.response?.status
    if (status === 429) {
      const retryAfter = err.retryAfter
      errorTitle.value = 'Account Locked'
      errorMessage.value = retryAfter
        ? `Too many failed attempts. Please try again in ${retryAfter} seconds.`
        : 'Too many failed attempts. Please wait before trying again.'
    } else if (status === 401) {
      errorTitle.value = 'Login Failed'
      errorMessage.value = 'Invalid username or password. Please try again.'
    } else {
      errorTitle.value = 'Login Failed'
      errorMessage.value = err.response?.data?.message || 'An unexpected error occurred. Please try again.'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.login-view {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 80vh;
  padding: 24px;
}

.login-card {
  width: 100%;
  max-width: 420px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  padding: 40px 32px;
}

.login-card h1 {
  margin: 0 0 4px;
  font-size: 1.75rem;
  color: #1a237e;
}

.subtitle {
  margin: 0 0 24px;
  color: #666;
  font-size: 0.9rem;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
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
  margin-top: 8px;
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

.account-link {
  margin-top: 20px;
  text-align: center;
  font-size: 0.875rem;
  color: #666;
}

.account-link a {
  color: #1a237e;
  text-decoration: none;
  font-weight: 600;
}

.account-link a:hover {
  text-decoration: underline;
}
</style>
