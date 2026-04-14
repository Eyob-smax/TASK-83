<template>
  <nav class="app-sidebar" v-if="authStore.isAuthenticated">
    <ul class="nav-list">
      <li><router-link to="/">Sessions</router-link></li>
      <li><router-link to="/waitlist">My Waitlist</router-link></li>
      <li><router-link to="/notifications">Notifications</router-link></li>
      <li v-if="authStore.hasAnyRole(['EVENT_STAFF', 'SYSTEM_ADMIN'])">
        <router-link to="/checkin">Check-In</router-link>
      </li>
      <li v-if="authStore.hasAnyRole(['EVENT_STAFF', 'SYSTEM_ADMIN'])">
        <router-link to="/imports">Imports</router-link>
      </li>
      <li v-if="authStore.hasAnyRole(['FINANCE_MANAGER', 'SYSTEM_ADMIN'])">
        <router-link to="/finance">Finance</router-link>
      </li>
      <li v-if="authStore.hasAnyRole(['EVENT_STAFF', 'FINANCE_MANAGER', 'SYSTEM_ADMIN'])">
        <router-link to="/exports">Exports</router-link>
      </li>
      <li v-if="authStore.hasRole('SYSTEM_ADMIN')">
        <router-link to="/admin/audit">Audit Log</router-link>
      </li>
      <li v-if="authStore.hasRole('SYSTEM_ADMIN')">
        <router-link to="/admin/users">Users</router-link>
      </li>
      <li v-if="authStore.hasRole('SYSTEM_ADMIN')">
        <router-link to="/admin/backups">Backups</router-link>
      </li>
      <li v-if="authStore.hasRole('SYSTEM_ADMIN')">
        <router-link to="/admin/security">Security</router-link>
      </li>
    </ul>
  </nav>
</template>

<script setup>
import { useAuthStore } from '@/stores/auth'
const authStore = useAuthStore()
</script>

<style scoped>
.app-sidebar { width: 220px; background: #f0f0f0; padding: 16px 0; border-right: 1px solid #ddd; }
.nav-list { list-style: none; padding: 0; margin: 0; }
.nav-list li { padding: 0; }
.nav-list a { display: block; padding: 10px 20px; color: #333; text-decoration: none; font-size: 0.9rem; }
.nav-list a:hover, .nav-list a.router-link-active { background: #e0e0e0; color: #1a237e; font-weight: 500; }
</style>
