<template>
  <div v-if="!offlineStore.isOnline" class="offline-banner" role="alert">
    You are currently offline. Changes will be queued and synced when connectivity returns.
  </div>
</template>

<script setup>
import { useOfflineStore } from '@/stores/offline'
const offlineStore = useOfflineStore()
</script>

<style scoped>
.offline-banner { background: #ff9800; color: white; padding: 8px 16px; text-align: center; font-size: 0.9rem; border-radius: 4px; margin-bottom: 12px; }
</style>
