import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('@/utils/offline', () => ({
  offlineQueue: { keys: vi.fn(async () => []), getItem: vi.fn(), setItem: vi.fn(), removeItem: vi.fn() },
  rosterCache: { getItem: vi.fn(), setItem: vi.fn(), removeItem: vi.fn() }
}))
vi.mock('@/utils/resumableUpload', () => ({ uploadAttachmentResumable: vi.fn() }))

import App from '../src/App.vue'
import { useAuthStore } from '../src/stores/auth.js'

async function mountApp(route = '/') {
  setActivePinia(createPinia())
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div class="home" />' } },
      { path: '/login', component: { template: '<div class="login" />' }, meta: { layout: 'auth' } }
    ]
  })
  await router.push(route)
  await router.isReady()
  return { wrapper: mount(App, { global: { plugins: [pinia, router] } }), pinia }
}

describe('App.vue', () => {
  beforeEach(() => { sessionStorage.clear() })

  it('mounts successfully with main layout by default', async () => {
    const { wrapper } = await mountApp('/')
    await flushPromises()
    expect(wrapper.find('.main-layout').exists()).toBe(true)
  })

  it('uses auth layout for routes with layout: auth meta', async () => {
    const { wrapper } = await mountApp('/login')
    await flushPromises()
    expect(wrapper.find('.auth-layout').exists()).toBe(true)
  })

  it('renders router-view inside the chosen layout', async () => {
    const { wrapper } = await mountApp('/')
    await flushPromises()
    expect(wrapper.find('.home').exists()).toBe(true)
  })

  it('initFromSession populates auth store on mount', async () => {
    sessionStorage.setItem('eventops_user', JSON.stringify({ id: 'u9', roleType: 'SYSTEM_ADMIN' }))
    const { wrapper } = await mountApp('/')
    await flushPromises()
    const auth = useAuthStore()
    expect(auth.user?.id).toBe('u9')
    expect(wrapper.exists()).toBe(true)
  })
})
