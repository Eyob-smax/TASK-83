import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('../../src/api/auth.js', () => ({
  login: vi.fn(),
  logout: vi.fn(),
  createAccount: vi.fn(),
  refreshSession: vi.fn(),
  getCurrentUser: vi.fn()
}))

import { login as apiLogin } from '../../src/api/auth.js'
import LoginView from '../../src/views/auth/LoginView.vue'

async function mountLogin(routeQuery = {}) {
  setActivePinia(createPinia())
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/account', component: { template: '<div />' } },
      { path: '/login', component: LoginView }
    ]
  })
  await router.push({ path: '/login', query: routeQuery })
  await router.isReady()
  return { wrapper: mount(LoginView, { global: { plugins: [createPinia(), router] } }), router }
}

describe('LoginView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
  })

  it('renders username and password inputs and the Sign In button', async () => {
    const { wrapper } = await mountLogin()
    expect(wrapper.find('#username').exists()).toBe(true)
    expect(wrapper.find('#password').exists()).toBe(true)
    expect(wrapper.find('button[type="submit"]').text()).toContain('Sign In')
  })

  it('disables submit button when form is empty', async () => {
    const { wrapper } = await mountLogin()
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()
  })

  it('enables submit button when both fields have content', async () => {
    const { wrapper } = await mountLogin()
    await wrapper.find('#username').setValue('bob')
    await wrapper.find('#password').setValue('secret')
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeUndefined()
  })

  it('shows 401 message when login fails with 401', async () => {
    apiLogin.mockRejectedValue({ response: { status: 401 } })
    const { wrapper } = await mountLogin()
    await wrapper.find('#username').setValue('bob')
    await wrapper.find('#password').setValue('bad')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    expect(wrapper.text()).toContain('Invalid username or password')
  })

  it('shows 429 message with retry seconds when rate limited', async () => {
    apiLogin.mockRejectedValue({ response: { status: 429 }, retryAfter: 60 })
    const { wrapper } = await mountLogin()
    await wrapper.find('#username').setValue('bob')
    await wrapper.find('#password').setValue('x')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    expect(wrapper.text()).toContain('60 seconds')
    expect(wrapper.text()).toContain('Account Locked')
  })

  it('shows 429 message without retryAfter falls back to generic message', async () => {
    apiLogin.mockRejectedValue({ response: { status: 429 } })
    const { wrapper } = await mountLogin()
    await wrapper.find('#username').setValue('bob')
    await wrapper.find('#password').setValue('x')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    expect(wrapper.text()).toContain('Too many failed attempts')
  })

  it('clears error when dismiss is clicked', async () => {
    apiLogin.mockRejectedValue({ response: { status: 401 } })
    const { wrapper } = await mountLogin()
    await wrapper.find('#username').setValue('bob')
    await wrapper.find('#password').setValue('x')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    expect(wrapper.text()).toContain('Invalid username')
    const dismiss = wrapper.findAll('button').find(b => b.text() === 'Dismiss')
    await dismiss.trigger('click')
    expect(wrapper.text()).not.toContain('Invalid username')
  })
})
