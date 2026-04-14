import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listEvents, getEvent, getAvailability } from '@/api/events'

export const useEventsStore = defineStore('events', () => {
  const events = ref([])
  const currentEvent = ref(null)
  const pagination = ref({ page: 0, totalPages: 0, totalElements: 0 })
  const loading = ref(false)
  const error = ref(null)

  async function fetchEvents(params = {}) {
    loading.value = true
    error.value = null
    try {
      const res = await listEvents(params)
      const page = res.data.data
      events.value = page.content
      pagination.value = { page: page.page, totalPages: page.totalPages, totalElements: page.totalElements }
    } catch (e) {
      error.value = e.response?.data?.message || 'Failed to load events'
    } finally {
      loading.value = false
    }
  }

  async function fetchEvent(id) {
    loading.value = true
    error.value = null
    try {
      const res = await getEvent(id)
      currentEvent.value = res.data.data
    } catch (e) {
      error.value = e.response?.data?.message || 'Failed to load event'
    } finally {
      loading.value = false
    }
  }

  async function fetchAvailability(id) {
    try {
      const res = await getAvailability(id)
      currentEvent.value = res.data.data
    } catch (e) { /* silent refresh failure */ }
  }

  return { events, currentEvent, pagination, loading, error, fetchEvents, fetchEvent, fetchAvailability }
})
