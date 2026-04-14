import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import MaskedField from '../../src/components/common/MaskedField.vue'

describe('MaskedField', () => {
  it('renders masked value by default', () => {
    const wrapper = mount(MaskedField, { props: { value: 'sensitive@email.com' } })
    const text = wrapper.text()
    expect(text).toContain('****')
    expect(text).toContain('.com')
    expect(text).not.toContain('sensitive@email')
  })

  it('shows dash for null value', () => {
    const wrapper = mount(MaskedField, { props: { value: null } })
    expect(wrapper.text()).toContain('—')
  })

  it('shows dash for empty value', () => {
    const wrapper = mount(MaskedField, { props: { value: '' } })
    expect(wrapper.text()).toContain('—')
  })

  it('fully masks short values', () => {
    const wrapper = mount(MaskedField, { props: { value: 'abc' } })
    const text = wrapper.text()
    expect(text).toContain('***')
    expect(text).not.toContain('abc')
  })

  it('shows toggle button when revealable', () => {
    const wrapper = mount(MaskedField, { props: { value: 'test data', revealable: true } })
    expect(wrapper.find('button').exists()).toBe(true)
  })

  it('hides toggle button when not revealable', () => {
    const wrapper = mount(MaskedField, { props: { value: 'test data', revealable: false } })
    expect(wrapper.find('button').exists()).toBe(false)
  })

  it('reveals value on toggle click', async () => {
    const wrapper = mount(MaskedField, { props: { value: 'secret@test.com' } })
    await wrapper.find('button').trigger('click')
    expect(wrapper.text()).toContain('secret@test.com')
  })

  it('hides value on second toggle click', async () => {
    const wrapper = mount(MaskedField, { props: { value: 'secret@test.com' } })
    await wrapper.find('button').trigger('click')
    await wrapper.find('button').trigger('click')
    expect(wrapper.text()).not.toContain('secret@test.com')
    expect(wrapper.text()).toContain('****')
  })
})
