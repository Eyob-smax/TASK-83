import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import AppSidebar from '../../src/components/common/AppSidebar.vue'
import { useAuthStore } from '../../src/stores/auth.js'

const stubRoute = { template: '<div />' }

async function mountSidebar(roleType) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: stubRoute },
      { path: '/waitlist', component: stubRoute },
      { path: '/notifications', component: stubRoute },
      { path: '/checkin', component: stubRoute },
      { path: '/imports', component: stubRoute },
      { path: '/finance', component: stubRoute },
      { path: '/exports', component: stubRoute },
      { path: '/admin/audit', component: stubRoute },
      { path: '/admin/users', component: stubRoute },
      { path: '/admin/backups', component: stubRoute },
      { path: '/admin/security', component: stubRoute }
    ]
  })
  await router.push('/')
  await router.isReady()

  const auth = useAuthStore()
  if (roleType) {
    auth.user = { id: 'u1', roleType }
  }

  return mount(AppSidebar, { global: { plugins: [pinia, router] } })
}

describe('AppSidebar', () => {
  it('renders nothing when unauthenticated', async () => {
    const wrapper = await mountSidebar(null)
    expect(wrapper.find('nav').exists()).toBe(false)
  })

  it('ATTENDEE sees 3 public nav items', async () => {
    const wrapper = await mountSidebar('ATTENDEE')
    expect(wrapper.findAll('li')).toHaveLength(3)
    expect(wrapper.text()).toContain('Sessions')
    expect(wrapper.text()).toContain('My Waitlist')
    expect(wrapper.text()).toContain('Notifications')
    expect(wrapper.text()).not.toContain('Check-In')
  })

  it('EVENT_STAFF sees 6 items (public + check-in, imports, exports)', async () => {
    const wrapper = await mountSidebar('EVENT_STAFF')
    expect(wrapper.findAll('li')).toHaveLength(6)
    expect(wrapper.text()).toContain('Check-In')
    expect(wrapper.text()).toContain('Imports')
    expect(wrapper.text()).toContain('Exports')
    expect(wrapper.text()).not.toContain('Finance')
  })

  it('FINANCE_MANAGER sees 5 items (public + finance, exports)', async () => {
    const wrapper = await mountSidebar('FINANCE_MANAGER')
    expect(wrapper.findAll('li')).toHaveLength(5)
    expect(wrapper.text()).toContain('Finance')
    expect(wrapper.text()).toContain('Exports')
    expect(wrapper.text()).not.toContain('Check-In')
    expect(wrapper.text()).not.toContain('Audit Log')
  })

  it('SYSTEM_ADMIN sees all 11 items', async () => {
    const wrapper = await mountSidebar('SYSTEM_ADMIN')
    expect(wrapper.findAll('li')).toHaveLength(11)
    expect(wrapper.text()).toContain('Audit Log')
    expect(wrapper.text()).toContain('Users')
    expect(wrapper.text()).toContain('Backups')
    expect(wrapper.text()).toContain('Security')
  })
})
