import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AuthLayout from '../../src/components/layout/AuthLayout.vue'

describe('AuthLayout', () => {
  it('mounts successfully', () => {
    const wrapper = mount(AuthLayout)
    expect(wrapper.exists()).toBe(true)
  })

  it('renders the Event Operations Platform title', () => {
    const wrapper = mount(AuthLayout)
    expect(wrapper.text()).toContain('Event Operations Platform')
  })

  it('renders slot content inside the auth card', () => {
    const wrapper = mount(AuthLayout, {
      slots: { default: '<div class="slot-marker">inner</div>' }
    })
    expect(wrapper.find('.slot-marker').exists()).toBe(true)
    expect(wrapper.text()).toContain('inner')
  })
})
