<template>
  <div v-if="message" class="error-alert" role="alert">
    <strong>{{ title }}</strong>
    <p>{{ message }}</p>
    <button v-if="retryable" @click="$emit('retry')">Retry</button>
    <button v-if="dismissable" @click="$emit('dismiss')">Dismiss</button>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, default: 'Error' },
  message: { type: String, required: true },
  retryable: { type: Boolean, default: false },
  dismissable: { type: Boolean, default: true }
})
defineEmits(['retry', 'dismiss'])
</script>

<style scoped>
.error-alert { background: #ffebee; border: 1px solid #ef9a9a; border-radius: 4px; padding: 12px 16px; margin-bottom: 12px; color: #c62828; }
.error-alert p { margin: 4px 0 8px; }
.error-alert button { margin-right: 8px; padding: 4px 12px; border: 1px solid #c62828; background: white; color: #c62828; border-radius: 4px; cursor: pointer; }
</style>
