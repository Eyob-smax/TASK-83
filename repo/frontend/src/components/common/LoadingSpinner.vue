<template>
  <div class="loading-spinner" role="status">
    <div class="spinner"></div>
    <span>{{ message }}</span>
  </div>
</template>

<script setup>
defineProps({ message: { type: String, default: 'Loading...' } })
</script>

<style scoped>
.loading-spinner { display: flex; align-items: center; gap: 8px; padding: 16px; color: #666; }
.spinner { width: 20px; height: 20px; border: 3px solid #ddd; border-top: 3px solid #1a237e; border-radius: 50%; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
