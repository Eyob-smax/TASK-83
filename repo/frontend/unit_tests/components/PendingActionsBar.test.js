import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/utils/offline', () => ({
  offlineQueue: { keys: vi.fn(async () => []), getItem: vi.fn(), setItem: vi.fn(), removeItem: vi.fn() },
  rosterCache: { getItem: vi.fn(), setItem: vi.fn(), removeItem: vi.fn() }
}))

vi.mock('@/utils/resumableUpload', () => ({ uploadAttachmentResumable: vi.fn() }))

import PendingActionsBar from '../../src/components/offline/PendingActionsBar.vue'
import { useOfflineStore } from '../../src/stores/offline.js'

describe('PendingActionsBar', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders nothing when no pending actions and no conflicts', () => {
    const wrapper = mount(PendingActionsBar)
    expect(wrapper.find('.pending-bar').exists()).toBe(false)
    expect(wrapper.find('.conflict-bar').exists()).toBe(false)
  })

  it('shows pending bar with count when actions are queued', () => {
    const store = useOfflineStore()
    store.pendingActions = [{ id: '1' }, { id: '2' }]
    store.isOnline = true
    store.syncInProgress = false
    const wrapper = mount(PendingActionsBar)
    expect(wrapper.find('.pending-bar').exists()).toBe(true)
    expect(wrapper.text()).toContain('2 pending action(s)')
  })

  it('shows "Sync Now" button when online and not syncing', () => {
    const store = useOfflineStore()
    store.pendingActions = [{ id: '1' }]
    store.isOnline = true
    store.syncInProgress = false
    const wrapper = mount(PendingActionsBar)
    expect(wrapper.findAll('button').map(b => b.text())).toContain('Sync Now')
  })

  it('shows "Syncing..." text while sync is in progress', () => {
    const store = useOfflineStore()
    store.pendingActions = [{ id: '1' }]
    store.isOnline = true
    store.syncInProgress = true
    const wrapper = mount(PendingActionsBar)
    expect(wrapper.text()).toContain('Syncing...')
    expect(wrapper.findAll('button').map(b => b.text())).not.toContain('Sync Now')
  })

  it('renders a conflict entry for each conflictedAction', () => {
    const store = useOfflineStore()
    store.conflictedActions = [
      { key: 'k1', code: 'PERIOD_CLOSED', message: 'Cannot post to closed period' }
    ]
    const wrapper = mount(PendingActionsBar)
    expect(wrapper.find('.conflict-bar').exists()).toBe(true)
    expect(wrapper.text()).toContain('PERIOD_CLOSED')
    expect(wrapper.text()).toContain('Cannot post to closed period')
  })

  it('dismiss button removes the conflict from store via dismissConflict', async () => {
    const store = useOfflineStore()
    store.conflictedActions = [{ key: 'k1', code: 'X', message: 'y' }]
    const wrapper = mount(PendingActionsBar)
    await wrapper.findAll('button').find(b => b.text() === 'Dismiss').trigger('click')
    expect(store.conflictedActions).toHaveLength(0)
  })
})
