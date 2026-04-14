import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as notifApi from '@/api/notifications'

export const useNotificationsStore = defineStore('notifications', () => {
  const notifications = ref([])
  const unreadCount = ref(0)
  const subscriptions = ref([])
  const dndSettings = ref(null)
  const pagination = ref({ page: 0, totalPages: 0, totalElements: 0 })
  const loading = ref(false)
  const error = ref(null)

  async function fetchNotifications(params = {}) {
    loading.value = true
    error.value = null
    try {
      const res = await notifApi.listNotifications(params)
      const page = res.data.data
      notifications.value = page.content
      pagination.value = { page: page.page, totalPages: page.totalPages, totalElements: page.totalElements }
    } catch (e) {
      error.value = e.response?.data?.message || 'Failed to load notifications'
    } finally {
      loading.value = false
    }
  }

  async function fetchUnreadCount() {
    try {
      const res = await notifApi.getUnreadCount()
      unreadCount.value = res.data.data.count
    } catch (e) { /* silent */ }
  }

  async function markAsRead(id) {
    try {
      await notifApi.markAsRead(id)
      const notif = notifications.value.find(n => n.id === id)
      if (notif) notif.read = true
      if (unreadCount.value > 0) unreadCount.value--
    } catch (e) {
      error.value = e.response?.data?.message || 'Failed to mark as read'
    }
  }

  async function fetchSubscriptions() {
    try {
      const res = await notifApi.getSubscriptions()
      subscriptions.value = res.data.data
    } catch (e) {
      error.value = e.response?.data?.message || 'Failed to load subscriptions'
    }
  }

  async function updateSubscriptions(subs) {
    try {
      await notifApi.updateSubscriptions(subs)
      subscriptions.value = subs
    } catch (e) {
      error.value = e.response?.data?.message || 'Failed to update subscriptions'
    }
  }

  async function fetchDndSettings() {
    try {
      const res = await notifApi.getDndSettings()
      dndSettings.value = res.data.data
    } catch (e) {
      error.value = e.response?.data?.message || 'Failed to load DND settings'
    }
  }

  async function updateDnd(settings) {
    try {
      const res = await notifApi.updateDndSettings(settings)
      dndSettings.value = res.data.data
    } catch (e) {
      error.value = e.response?.data?.message || 'Failed to update DND settings'
    }
  }

  return { notifications, unreadCount, subscriptions, dndSettings, pagination, loading, error, fetchNotifications, fetchUnreadCount, markAsRead, fetchSubscriptions, updateSubscriptions, fetchDndSettings, updateDnd }
})
