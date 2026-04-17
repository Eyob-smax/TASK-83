import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('@/utils/offline', () => ({
  offlineQueue: { keys: vi.fn(async () => []), getItem: vi.fn(), setItem: vi.fn(), removeItem: vi.fn() },
  rosterCache: { getItem: vi.fn(), setItem: vi.fn(), removeItem: vi.fn() }
}))

vi.mock('@/utils/resumableUpload', () => ({ uploadAttachmentResumable: vi.fn() }))

import MainLayout from '../../src/components/layout/MainLayout.vue'

async function mountLayout() {
  const pinia = createPinia()
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/notifications', component: { template: '<div />' } },
      { path: '/login', component: { template: '<div />' } }
    ]
  })
  await router.push('/')
  await router.isReady()
  return mount(MainLayout, {
    global: { plugins: [pinia, router] },
    slots: { default: '<div class="slot-marker">main</div>' }
  })
}

describe('MainLayout', () => {
  it('mounts successfully', async () => {
    const wrapper = await mountLayout()
    expect(wrapper.exists()).toBe(true)
  })

  it('renders a <main> content area', async () => {
    const wrapper = await mountLayout()
    expect(wrapper.find('main').exists()).toBe(true)
  })

  it('renders slot content', async () => {
    const wrapper = await mountLayout()
    expect(wrapper.find('.slot-marker').exists()).toBe(true)
  })

  it('renders header and footer wrappers', async () => {
    const wrapper = await mountLayout()
    expect(wrapper.find('header').exists()).toBe(true)
    expect(wrapper.find('footer').exists()).toBe(true)
  })
})
