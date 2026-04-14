<template>
  <div v-if="offlineStore.pendingActions.length > 0" class="pending-bar">
    <span>{{ offlineStore.pendingActions.length }} pending action(s) waiting to sync</span>
    <button v-if="offlineStore.isOnline && !offlineStore.syncInProgress" @click="offlineStore.replayPendingActions()">
      Sync Now
    </button>
    <span v-if="offlineStore.syncInProgress">Syncing...</span>
  </div>
</template>

<script setup>
import { useOfflineStore } from '@/stores/offline'
const offlineStore = useOfflineStore()
</script>

<style scoped>
.pending-bar { background: #2196f3; color: white; padding: 8px 16px; display: flex; justify-content: space-between; align-items: center; border-radius: 4px; margin-bottom: 12px; font-size: 0.9rem; }
.pending-bar button { background: white; color: #2196f3; border: none; padding: 4px 12px; border-radius: 4px; cursor: pointer; }
</style>
