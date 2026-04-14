<template>
  <header class="app-header">
    <div class="header-left">
      <router-link to="/" class="brand">EventOps</router-link>
    </div>
    <div class="header-right">
      <router-link v-if="authStore.isAuthenticated" to="/notifications" class="notif-badge">
        Notifications
        <span v-if="notificationsStore.unreadCount > 0" class="badge">{{ notificationsStore.unreadCount }}</span>
      </router-link>
      <span v-if="authStore.isAuthenticated" class="user-info">
        {{ authStore.user?.displayName }} ({{ authStore.user?.roleType }})
      </span>
      <button v-if="authStore.isAuthenticated" class="btn-logout" @click="handleLogout">Logout</button>
    </div>
  </header>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNotificationsStore } from '@/stores/notifications'

const router = useRouter()
const authStore = useAuthStore()
const notificationsStore = useNotificationsStore()

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.app-header { display: flex; justify-content: space-between; align-items: center; padding: 12px 20px; background: #1a237e; color: white; }
.brand { color: white; text-decoration: none; font-weight: bold; font-size: 1.2rem; }
.header-right { display: flex; align-items: center; gap: 16px; }
.notif-badge { color: white; text-decoration: none; position: relative; }
.badge { background: #f44336; border-radius: 50%; padding: 2px 6px; font-size: 0.75rem; margin-left: 4px; }
.user-info { font-size: 0.85rem; opacity: 0.9; }
.btn-logout { background: transparent; border: 1px solid rgba(255,255,255,0.5); color: white; padding: 4px 12px; border-radius: 4px; cursor: pointer; }
</style>
