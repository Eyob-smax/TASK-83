<template>
  <component :is="layoutComponent">
    <router-view />
  </component>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useOfflineStore } from '@/stores/offline'
import MainLayout from '@/components/layout/MainLayout.vue'
import AuthLayout from '@/components/layout/AuthLayout.vue'

const route = useRoute()
const authStore = useAuthStore()
const offlineStore = useOfflineStore()

const layoutComponent = computed(() => {
  return route.meta.layout === 'auth' ? AuthLayout : MainLayout
})

onMounted(() => {
  authStore.initFromSession()
  offlineStore.initListeners()
})
</script>
