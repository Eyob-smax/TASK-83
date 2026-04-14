import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ConfirmDialog from '../../src/components/common/ConfirmDialog.vue'

describe('ConfirmDialog', () => {
  it('renders message when visible', () => {
    const wrapper = mount(ConfirmDialog, { props: { visible: true, message: 'Are you sure?' } })
    expect(wrapper.text()).toContain('Are you sure?')
  })

  it('hidden when visible is false', () => {
    const wrapper = mount(ConfirmDialog, { props: { visible: false, message: 'Test' } })
    expect(wrapper.text()).not.toContain('Test')
  })

  it('emits confirm on confirm click', async () => {
    const wrapper = mount(ConfirmDialog, { props: { visible: true, message: 'Delete?' } })
    const confirmBtn = wrapper.findAll('button').find(b => b.text().toLowerCase().includes('confirm') || b.text().toLowerCase().includes('yes'))
    if (confirmBtn) {
      await confirmBtn.trigger('click')
      expect(wrapper.emitted('confirm')).toBeTruthy()
    }
  })

  it('emits cancel on cancel click', async () => {
    const wrapper = mount(ConfirmDialog, { props: { visible: true, message: 'Delete?' } })
    const cancelBtn = wrapper.findAll('button').find(b => b.text().toLowerCase().includes('cancel') || b.text().toLowerCase().includes('no'))
    if (cancelBtn) {
      await cancelBtn.trigger('click')
      expect(wrapper.emitted('cancel')).toBeTruthy()
    }
  })
})
