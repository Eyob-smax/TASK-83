<template>
  <div class="account-view">
    <div class="account-card">
      <h1>Create Account</h1>
      <p class="subtitle">Register as a new attendee</p>

      <ErrorAlert
        v-if="errorMessage"
        :message="errorMessage"
        :title="errorTitle"
        :dismissable="true"
        @dismiss="clearError"
      />

      <div v-if="successMessage" class="success-alert" role="status">
        <strong>Account Created</strong>
        <p>{{ successMessage }}</p>
      </div>

      <form v-if="!successMessage" @submit.prevent="handleCreateAccount" class="account-form" novalidate>
        <div class="form-group">
          <label for="username">Username</label>
          <input
            id="username"
            v-model.trim="form.username"
            type="text"
            autocomplete="username"
            placeholder="Choose a username"
            :disabled="submitting"
            required
          />
        </div>

        <div class="form-group">
          <label for="displayName">Display Name</label>
          <input
            id="displayName"
            v-model.trim="form.displayName"
            type="text"
            autocomplete="name"
            placeholder="Your full name"
            :disabled="submitting"
            required
          />
        </div>

        <div class="form-group">
          <label for="contactInfo">Contact Info</label>
          <input
            id="contactInfo"
            v-model.trim="form.contactInfo"
            type="text"
            autocomplete="email"
            placeholder="Email or phone number"
            :disabled="submitting"
            required
          />
        </div>

        <div class="form-group">
          <label for="password">Password</label>
          <input
            id="password"
            v-model="form.password"
            type="password"
            autocomplete="new-password"
            placeholder="Create a password"
            :disabled="submitting"
            required
          />
        </div>

        <div class="form-group">
          <label for="confirmPassword">Confirm Password</label>
          <input
            id="confirmPassword"
            v-model="form.confirmPassword"
            type="password"
            autocomplete="new-password"
            placeholder="Confirm your password"
            :disabled="submitting"
            :class="{ 'input-error': passwordMismatch }"
            required
          />
          <span v-if="passwordMismatch" class="field-error">Passwords do not match</span>
        </div>

        <button type="submit" class="btn-primary" :disabled="submitting || !isFormValid">
          <span v-if="submitting" class="btn-spinner"></span>
          {{ submitting ? 'Creating Account...' : 'Create Account' }}
        </button>
      </form>

      <p class="login-link">
        Already have an account?
        <router-link to="/login">Sign in</router-link>
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { createAccount } from '@/api/auth'
import ErrorAlert from '@/components/common/ErrorAlert.vue'

const router = useRouter()

const form = reactive({
  username: '',
  displayName: '',
  contactInfo: '',
  password: '',
  confirmPassword: ''
})

const submitting = ref(false)
const errorMessage = ref('')
const errorTitle = ref('Error')
const successMessage = ref('')

const passwordMismatch = computed(
  () => form.confirmPassword.length > 0 && form.password !== form.confirmPassword
)

const isFormValid = computed(
  () =>
    form.username.length > 0 &&
    form.displayName.length > 0 &&
    form.contactInfo.length > 0 &&
    form.password.length > 0 &&
    form.confirmPassword.length > 0 &&
    !passwordMismatch.value
)

function clearError() {
  errorMessage.value = ''
  errorTitle.value = 'Error'
}

async function handleCreateAccount() {
  if (!isFormValid.value || submitting.value) return

  clearError()
  submitting.value = true

  try {
    await createAccount({
      username: form.username,
      password: form.password,
      displayName: form.displayName,
      contactInfo: form.contactInfo
    })
    successMessage.value = 'Your account has been created successfully. Redirecting to login...'
    setTimeout(() => {
      router.push('/login')
    }, 2000)
  } catch (err) {
    const status = err.response?.status
    const errData = err.response?.data

    if (status === 409 || errData?.errors?.[0]?.code === 'DUPLICATE_USERNAME') {
      errorTitle.value = 'Username Taken'
      errorMessage.value = 'This username is already in use. Please choose a different one.'
    } else {
      errorTitle.value = 'Registration Failed'
      errorMessage.value = errData?.message || 'An unexpected error occurred. Please try again.'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.account-view {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 80vh;
  padding: 24px;
}

.account-card {
  width: 100%;
  max-width: 480px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  padding: 40px 32px;
}

.account-card h1 {
  margin: 0 0 4px;
  font-size: 1.75rem;
  color: #1a237e;
}

.subtitle {
  margin: 0 0 24px;
  color: #666;
  font-size: 0.9rem;
}

.account-form {
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

.form-group input.input-error {
  border-color: #c62828;
}

.field-error {
  color: #c62828;
  font-size: 0.8rem;
}

.success-alert {
  background: #e8f5e9;
  border: 1px solid #a5d6a7;
  border-radius: 4px;
  padding: 12px 16px;
  margin-bottom: 16px;
  color: #2e7d32;
}

.success-alert p {
  margin: 4px 0 0;
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

.login-link {
  margin-top: 20px;
  text-align: center;
  font-size: 0.875rem;
  color: #666;
}

.login-link a {
  color: #1a237e;
  text-decoration: none;
  font-weight: 600;
}

.login-link a:hover {
  text-decoration: underline;
}
</style>
