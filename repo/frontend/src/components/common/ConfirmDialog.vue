<!--
  ConfirmDialog.vue
  Modal confirmation dialog for destructive actions such as
  deleting events, removing volunteers, or cancelling registrations.
-->
<template>
  <div v-if="visible" class="confirm-dialog" role="dialog">
    <div class="confirm-dialog__backdrop" />
    <div class="confirm-dialog__content">
      <p>{{ message }}</p>
      <button @click="$emit('confirm')">Confirm</button>
      <button @click="$emit('cancel')">Cancel</button>
    </div>
  </div>
</template>

<script setup>
defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  message: {
    type: String,
    default: 'Are you sure?'
  }
})

defineEmits(['confirm', 'cancel'])
</script>
