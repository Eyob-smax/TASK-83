import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getPasscode, checkInAttendee, getRoster, getConflicts } from '@/api/checkin'

export const useCheckinStore = defineStore('checkin', () => {
  const currentPasscode = ref(null)
  const passcodeCountdown = ref(0)
  const roster = ref([])
  const conflicts = ref([])
  const loading = ref(false)
  const error = ref(null)
  const checkInResult = ref(null)
  const conflictDetail = ref(null)
  let countdownInterval = null

  async function fetchPasscode(sessionId) {
    try {
      const res = await getPasscode(sessionId)
      const data = res.data.data
      currentPasscode.value = data.passcode
      passcodeCountdown.value = data.remainingSeconds
      startCountdown()
    } catch (e) {
      error.value = e.response?.data?.message || 'Failed to load passcode'
    }
  }

  function startCountdown() {
    if (countdownInterval) clearInterval(countdownInterval)
    countdownInterval = setInterval(() => {
      if (passcodeCountdown.value > 0) {
        passcodeCountdown.value--
      } else {
        clearInterval(countdownInterval)
      }
    }, 1000)
  }

  async function performCheckIn(sessionId, data) {
    loading.value = true
    error.value = null
    checkInResult.value = null
    conflictDetail.value = null
    try {
      const res = await checkInAttendee(sessionId, data)
      checkInResult.value = res.data.data
      return res.data.data
    } catch (e) {
      const errData = e.response?.data
      const code = errData?.errors?.[0]?.code
      if (['DUPLICATE_CHECKIN', 'DEVICE_CONFLICT', 'CONCURRENT_DEVICE'].includes(code)) {
        conflictDetail.value = { code, message: errData.message }
      } else {
        error.value = errData?.message || 'Check-in failed'
      }
      throw e
    } finally {
      loading.value = false
    }
  }

  async function fetchRoster(sessionId) {
    loading.value = true
    try {
      const res = await getRoster(sessionId)
      roster.value = res.data.data
    } catch (e) {
      error.value = e.response?.data?.message || 'Failed to load roster'
    } finally {
      loading.value = false
    }
  }

  async function fetchConflicts(sessionId) {
    try {
      const res = await getConflicts(sessionId)
      conflicts.value = res.data.data
    } catch (e) { /* silent */ }
  }

  function clearConflict() { conflictDetail.value = null }

  return { currentPasscode, passcodeCountdown, roster, conflicts, loading, error, checkInResult, conflictDetail, fetchPasscode, performCheckIn, fetchRoster, fetchConflicts, clearConflict, startCountdown }
})
