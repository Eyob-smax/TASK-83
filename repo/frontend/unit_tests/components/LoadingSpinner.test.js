import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import LoadingSpinner from '../../src/components/common/LoadingSpinner.vue'

describe('LoadingSpinner', () => {
  it('mounts with default "Loading..." text', () => {
    const wrapper = mount(LoadingSpinner)
    expect(wrapper.text()).toContain('Loading...')
  })

  it('renders custom message prop', () => {
    const wrapper = mount(LoadingSpinner, { props: { message: 'Fetching data' } })
    expect(wrapper.text()).toContain('Fetching data')
    expect(wrapper.text()).not.toContain('Loading...')
  })

  it('marks the container with role="status" for a11y', () => {
    const wrapper = mount(LoadingSpinner)
    expect(wrapper.find('[role="status"]').exists()).toBe(true)
  })
})
