import { defineStore } from 'pinia'
import { ref } from 'vue'
import { register, listRegistrations, cancelRegistration, getWaitlistPositions } from '@/api/registration'

export const useRegistrationStore = defineStore('registration', () => {
  const registrations = ref([])
  const waitlistEntries = ref([])
  const loading = ref(false)
  const error = ref(null)
  const conflictMessage = ref(null)
  const lastResult = ref(null)

  async function registerForSession(sessionId) {
    loading.value = true
    error.value = null
    conflictMessage.value = null
    try {
      const res = await register(sessionId)
      lastResult.value = res.data.data
      return res.data.data
    } catch (e) {
      const errData = e.response?.data
      if (errData?.errors?.[0]?.code === 'DUPLICATE_REGISTRATION') {
        conflictMessage.value = errData.message || 'You are already registered for this session'
      } else {
        error.value = errData?.message || 'Registration failed'
      }
      throw e
    } finally {
      loading.value = false
    }
  }

  async function fetchRegistrations() {
    loading.value = true
    error.value = null
    try {
      const res = await listRegistrations()
      registrations.value = res.data.data
    } catch (e) {
      error.value = e.response?.data?.message || 'Failed to load registrations'
    } finally {
      loading.value = false
    }
  }

  async function cancelReg(id) {
    loading.value = true
    error.value = null
    try {
      await cancelRegistration(id)
      registrations.value = registrations.value.filter(r => r.id !== id)
    } catch (e) {
      error.value = e.response?.data?.message || 'Cancellation failed'
    } finally {
      loading.value = false
    }
  }

  async function fetchWaitlist() {
    loading.value = true
    try {
      const res = await getWaitlistPositions()
      waitlistEntries.value = res.data.data
    } catch (e) {
      error.value = e.response?.data?.message || 'Failed to load waitlist'
    } finally {
      loading.value = false
    }
  }

  function clearConflict() { conflictMessage.value = null }

  return { registrations, waitlistEntries, loading, error, conflictMessage, lastResult, registerForSession, fetchRegistrations, cancelReg, fetchWaitlist, clearConflict }
})
