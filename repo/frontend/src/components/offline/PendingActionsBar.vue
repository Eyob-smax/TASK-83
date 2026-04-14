<template>
  <div>
    <div v-if="offlineStore.pendingActions.length > 0" class="pending-bar">
      <span>{{ offlineStore.pendingActions.length }} pending action(s) waiting to sync</span>
      <button v-if="offlineStore.isOnline && !offlineStore.syncInProgress" @click="offlineStore.replayPendingActions()">
        Sync Now
      </button>
      <span v-if="offlineStore.syncInProgress">Syncing...</span>
    </div>
    <div
      v-for="conflict in offlineStore.conflictedActions"
      :key="conflict.key"
      class="conflict-bar"
    >
      <div class="conflict-info">
        <strong>Sync conflict: {{ conflict.code }}</strong>
        <span>{{ conflict.message }}</span>
      </div>
      <button @click="offlineStore.dismissConflict(conflict.key)">Dismiss</button>
    </div>
  </div>
</template>

<script setup>
import { useOfflineStore } from '@/stores/offline'
const offlineStore = useOfflineStore()
</script>

<style scoped>
.pending-bar { background: #2196f3; color: white; padding: 8px 16px; display: flex; justify-content: space-between; align-items: center; border-radius: 4px; margin-bottom: 12px; font-size: 0.9rem; }
.pending-bar button { background: white; color: #2196f3; border: none; padding: 4px 12px; border-radius: 4px; cursor: pointer; }
.conflict-bar { background: #e53935; color: white; padding: 8px 16px; display: flex; justify-content: space-between; align-items: center; border-radius: 4px; margin-bottom: 8px; font-size: 0.9rem; }
.conflict-info { display: flex; flex-direction: column; gap: 2px; }
.conflict-bar button { background: white; color: #e53935; border: none; padding: 4px 12px; border-radius: 4px; cursor: pointer; flex-shrink: 0; }
</style>
