<template>
  <div class="auth-layout">
    <div class="auth-card">
      <h1 class="auth-title">Event Operations Platform</h1>
      <slot />
    </div>
  </div>
</template>

<style scoped>
.auth-layout { display: flex; justify-content: center; align-items: center; min-height: 100vh; background: #f5f5f5; }
.auth-card { background: white; padding: 40px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); width: 100%; max-width: 440px; }
.auth-title { text-align: center; margin-bottom: 24px; font-size: 1.4rem; color: #333; }
</style>
