<template>
  <div class="notification-center-view">
    <div class="page-header">
      <h1>
        Notification Center
        <span v-if="notifStore.unreadCount > 0" class="unread-badge">
          {{ notifStore.unreadCount }}
        </span>
      </h1>
      <router-link to="/notifications/settings" class="settings-link">
        Subscription Settings
      </router-link>
    </div>

    <ErrorAlert
      v-if="notifStore.error"
      :message="notifStore.error"
      title="Error"
      :retryable="true"
      :dismissable="true"
      @retry="loadNotifications"
      @dismiss="notifStore.error = null"
    />

    <LoadingSpinner v-if="notifStore.loading" message="Loading notifications..." />

    <template v-else>
      <!-- Empty state -->
      <div v-if="notifStore.notifications.length === 0" class="empty-state">
        <div class="empty-icon">&#9993;</div>
        <p>No notifications yet</p>
        <span class="empty-hint">
          You'll see registration confirmations, waitlist updates, and system alerts here.
        </span>
      </div>

      <!-- Notification list -->
      <ul v-else class="notification-list">
        <li
          v-for="notif in notifStore.notifications"
          :key="notif.id"
          :class="['notification-item', { unread: !notif.read }]"
          @click="toggleExpand(notif)"
        >
          <div class="notification-row">
            <span class="unread-dot" v-if="!notif.read"></span>
            <span class="type-badge" :class="typeBadgeClass(notif.type)">
              {{ formatType(notif.type) }}
            </span>
            <div class="notification-content">
              <span :class="['notification-subject', { 'font-bold': !notif.read }]">
                {{ notif.subject || notif.title || 'Notification' }}
              </span>
              <span class="notification-preview" v-if="expandedId !== notif.id">
                {{ truncateBody(notif.body || notif.message || '') }}
              </span>
            </div>
            <span class="notification-time">{{ formatRelativeTime(notif.createdAt || notif.timestamp) }}</span>
          </div>

          <!-- Expanded body -->
          <div v-if="expandedId === notif.id" class="notification-body">
            <p>{{ notif.body || notif.message || 'No additional details.' }}</p>
            <span class="notification-full-time">
              {{ formatTime(notif.createdAt || notif.timestamp) }}
            </span>
          </div>
        </li>
      </ul>

      <!-- Pagination -->
      <div v-if="notifStore.pagination.totalPages > 1" class="pagination">
        <button
          class="btn-page"
          :disabled="currentPage <= 0"
          @click="goToPage(currentPage - 1)"
        >
          Previous
        </button>
        <span class="page-info">
          Page {{ currentPage + 1 }} of {{ notifStore.pagination.totalPages }}
          ({{ notifStore.pagination.totalElements }} total)
        </span>
        <button
          class="btn-page"
          :disabled="currentPage >= notifStore.pagination.totalPages - 1"
          @click="goToPage(currentPage + 1)"
        >
          Next
        </button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useNotificationsStore } from '@/stores/notifications'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import ErrorAlert from '@/components/common/ErrorAlert.vue'

const notifStore = useNotificationsStore()

const currentPage = ref(0)
const expandedId = ref(null)
const PAGE_SIZE = 15

onMounted(async () => {
  await Promise.all([
    loadNotifications(),
    notifStore.fetchUnreadCount()
  ])
})

async function loadNotifications() {
  await notifStore.fetchNotifications({ page: currentPage.value, size: PAGE_SIZE })
}

async function goToPage(page) {
  currentPage.value = page
  await loadNotifications()
}

async function toggleExpand(notif) {
  if (expandedId.value === notif.id) {
    expandedId.value = null
    return
  }
  expandedId.value = notif.id
  if (!notif.read) {
    await notifStore.markAsRead(notif.id)
  }
}

function truncateBody(text) {
  if (!text) return ''
  return text.length > 100 ? text.substring(0, 100) + '...' : text
}

function formatType(type) {
  if (!type) return 'INFO'
  return type.replace(/_/g, ' ')
}

function typeBadgeClass(type) {
  if (!type) return 'badge-default'
  const t = type.toUpperCase()
  if (t.includes('CONFIRMATION') || t.includes('PROMOTION')) return 'badge-success'
  if (t.includes('EXPIRED') || t.includes('FAILED') || t.includes('EXCEPTION')) return 'badge-error'
  if (t.includes('WARNING') || t.includes('DEVICE')) return 'badge-warning'
  if (t.includes('SYSTEM') || t.includes('ALERT')) return 'badge-info'
  return 'badge-default'
}

function formatTime(isoString) {
  if (!isoString) return '--'
  try {
    return new Date(isoString).toLocaleString()
  } catch {
    return isoString
  }
}

function formatRelativeTime(isoString) {
  if (!isoString) return ''
  try {
    const date = new Date(isoString)
    const now = new Date()
    const diffMs = now - date
    const diffMins = Math.floor(diffMs / 60000)
    if (diffMins < 1) return 'just now'
    if (diffMins < 60) return `${diffMins}m ago`
    const diffHours = Math.floor(diffMins / 60)
    if (diffHours < 24) return `${diffHours}h ago`
    const diffDays = Math.floor(diffHours / 24)
    if (diffDays < 7) return `${diffDays}d ago`
    return date.toLocaleDateString()
  } catch {
    return ''
  }
}
</script>

<style scoped>
.notification-center-view {
  max-width: 800px;
  margin: 0 auto;
  padding: 24px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.page-header h1 {
  margin: 0;
  font-size: 1.75rem;
  color: #1a237e;
  display: flex;
  align-items: center;
  gap: 10px;
}

.unread-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 24px;
  padding: 0 7px;
  border-radius: 12px;
  background: #c62828;
  color: #fff;
  font-size: 0.8rem;
  font-weight: 700;
}

.settings-link {
  color: #1a237e;
  text-decoration: none;
  font-size: 0.9rem;
  font-weight: 600;
}

.settings-link:hover {
  text-decoration: underline;
}

/* Empty state */
.empty-state {
  text-align: center;
  padding: 60px 24px;
  color: #999;
}

.empty-icon {
  font-size: 3rem;
  margin-bottom: 12px;
  opacity: 0.5;
}

.empty-state p {
  font-size: 1.1rem;
  margin: 0 0 6px;
  color: #666;
}

.empty-hint {
  font-size: 0.85rem;
}

/* Notification list */
.notification-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.notification-item {
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 6px;
  padding: 14px 16px;
  cursor: pointer;
  transition: background 0.15s, box-shadow 0.15s;
}

.notification-item:hover {
  background: #fafafa;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.notification-item.unread {
  background: #e8eaf6;
  border-color: #c5cae9;
}

.notification-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.unread-dot {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #1a237e;
}

.type-badge {
  flex-shrink: 0;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  white-space: nowrap;
  letter-spacing: 0.02em;
}

.badge-success { background: #e8f5e9; color: #2e7d32; }
.badge-error { background: #ffebee; color: #c62828; }
.badge-warning { background: #fff3e0; color: #e65100; }
.badge-info { background: #e3f2fd; color: #1565c0; }
.badge-default { background: #f5f5f5; color: #666; }

.notification-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.notification-subject {
  font-size: 0.95rem;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.font-bold {
  font-weight: 700;
}

.notification-preview {
  font-size: 0.85rem;
  color: #888;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notification-time {
  flex-shrink: 0;
  font-size: 0.8rem;
  color: #999;
  white-space: nowrap;
}

/* Expanded body */
.notification-body {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #e0e0e0;
}

.notification-body p {
  margin: 0 0 8px;
  font-size: 0.9rem;
  color: #444;
  line-height: 1.5;
  white-space: pre-wrap;
}

.notification-full-time {
  font-size: 0.8rem;
  color: #999;
}

/* Pagination */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #e0e0e0;
}

.btn-page {
  padding: 8px 16px;
  background: #fff;
  border: 1px solid #ccc;
  border-radius: 4px;
  font-size: 0.875rem;
  cursor: pointer;
  color: #1a237e;
  font-weight: 600;
  transition: background 0.15s;
}

.btn-page:hover:not(:disabled) {
  background: #e8eaf6;
}

.btn-page:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  color: #999;
}

.page-info {
  font-size: 0.85rem;
  color: #666;
}
</style>
