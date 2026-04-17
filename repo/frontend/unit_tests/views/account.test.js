import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('../../src/api/auth.js', () => ({
  createAccount: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  refreshSession: vi.fn(),
  getCurrentUser: vi.fn()
}))

import { createAccount } from '../../src/api/auth.js'
import AccountView from '../../src/views/auth/AccountView.vue'

async function mountAccount() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/login', component: { template: '<div />' } },
      { path: '/account', component: AccountView }
    ]
  })
  await router.push('/account')
  await router.isReady()
  return mount(AccountView, { global: { plugins: [createPinia(), router] } })
}

function fill(wrapper, overrides = {}) {
  const defaults = {
    username: 'bob',
    displayName: 'Bob Jones',
    contactInfo: 'bob@example.com',
    password: 'pw',
    confirmPassword: 'pw'
  }
  const vals = { ...defaults, ...overrides }
  return Promise.all([
    wrapper.find('#username').setValue(vals.username),
    wrapper.find('#displayName').setValue(vals.displayName),
    wrapper.find('#contactInfo').setValue(vals.contactInfo),
    wrapper.find('#password').setValue(vals.password),
    wrapper.find('#confirmPassword').setValue(vals.confirmPassword)
  ])
}

describe('AccountView', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders all 5 form fields', async () => {
    const wrapper = await mountAccount()
    expect(wrapper.find('#username').exists()).toBe(true)
    expect(wrapper.find('#displayName').exists()).toBe(true)
    expect(wrapper.find('#contactInfo').exists()).toBe(true)
    expect(wrapper.find('#password').exists()).toBe(true)
    expect(wrapper.find('#confirmPassword').exists()).toBe(true)
  })

  it('shows password mismatch error when confirm differs', async () => {
    const wrapper = await mountAccount()
    await fill(wrapper, { confirmPassword: 'different' })
    expect(wrapper.text()).toContain('Passwords do not match')
  })

  it('submit is disabled while form invalid', async () => {
    const wrapper = await mountAccount()
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()
  })

  it('shows success message after account creation', async () => {
    createAccount.mockResolvedValue({ data: { data: { id: 'u1' } } })
    const wrapper = await mountAccount()
    await fill(wrapper)
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    expect(wrapper.text()).toContain('Account Created')
  })

  it('shows duplicate username error on 409', async () => {
    createAccount.mockRejectedValue({ response: { status: 409 } })
    const wrapper = await mountAccount()
    await fill(wrapper)
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    expect(wrapper.text()).toContain('Username Taken')
  })

  it('shows generic error message on other failures', async () => {
    createAccount.mockRejectedValue({ response: { status: 500, data: { message: 'boom' } } })
    const wrapper = await mountAccount()
    await fill(wrapper)
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    expect(wrapper.text()).toContain('boom')
  })
})
