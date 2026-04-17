import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ErrorAlert from '../../src/components/common/ErrorAlert.vue'

describe('ErrorAlert', () => {
  it('renders nothing when message is empty', () => {
    const wrapper = mount(ErrorAlert, { props: { message: '' } })
    expect(wrapper.find('.error-alert').exists()).toBe(false)
  })

  it('renders title and message', () => {
    const wrapper = mount(ErrorAlert, {
      props: { title: 'Oops', message: 'Something went wrong' }
    })
    expect(wrapper.text()).toContain('Oops')
    expect(wrapper.text()).toContain('Something went wrong')
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
  })

  it('shows Retry button only when retryable is true', () => {
    const a = mount(ErrorAlert, { props: { message: 'x' } })
    expect(a.findAll('button').map(b => b.text())).not.toContain('Retry')
    const b = mount(ErrorAlert, { props: { message: 'x', retryable: true } })
    expect(b.findAll('button').map(bn => bn.text())).toContain('Retry')
  })

  it('emits retry when Retry button is clicked', async () => {
    const wrapper = mount(ErrorAlert, { props: { message: 'x', retryable: true } })
    await wrapper.findAll('button').find(b => b.text() === 'Retry').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })

  it('emits dismiss when Dismiss button is clicked', async () => {
    const wrapper = mount(ErrorAlert, { props: { message: 'x' } })
    const dismissBtn = wrapper.findAll('button').find(b => b.text() === 'Dismiss')
    await dismissBtn.trigger('click')
    expect(wrapper.emitted('dismiss')).toHaveLength(1)
  })
})
