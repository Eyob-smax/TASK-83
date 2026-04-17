import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AppFooter from '../../src/components/common/AppFooter.vue'

describe('AppFooter', () => {
  it('mounts successfully', () => {
    const wrapper = mount(AppFooter)
    expect(wrapper.exists()).toBe(true)
  })

  it('renders a <footer> element', () => {
    const wrapper = mount(AppFooter)
    expect(wrapper.find('footer').exists()).toBe(true)
  })

  it('contains the EventOps Platform label', () => {
    const wrapper = mount(AppFooter)
    expect(wrapper.text()).toContain('EventOps Platform')
  })
})
