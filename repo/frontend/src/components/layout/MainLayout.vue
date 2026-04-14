<template>
  <div class="main-layout">
    <AppHeader />
    <div class="layout-body">
      <AppSidebar />
      <main class="main-content">
        <OfflineIndicator />
        <PendingActionsBar />
        <slot />
      </main>
    </div>
    <AppFooter />
  </div>
</template>

<script setup>
import AppHeader from '@/components/common/AppHeader.vue'
import AppSidebar from '@/components/common/AppSidebar.vue'
import AppFooter from '@/components/common/AppFooter.vue'
import OfflineIndicator from '@/components/offline/OfflineIndicator.vue'
import PendingActionsBar from '@/components/offline/PendingActionsBar.vue'
</script>

<style scoped>
.main-layout { display: flex; flex-direction: column; min-height: 100vh; }
.layout-body { display: flex; flex: 1; }
.main-content { flex: 1; padding: 20px; max-width: 1200px; }
</style>
