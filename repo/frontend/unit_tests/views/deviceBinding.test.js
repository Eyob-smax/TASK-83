import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('../../src/api/events.js', () => ({
  listEvents: vi.fn(),
  getEvent: vi.fn(),
  getAvailability: vi.fn()
}))

vi.mock('../../src/api/checkin.js', () => ({
  getPasscode: vi.fn(),
  checkInAttendee: vi.fn(),
  getRoster: vi.fn(),
  getConflicts: vi.fn()
}))

import { listEvents } from '../../src/api/events.js'
import { getRoster, getConflicts } from '../../src/api/checkin.js'
import DeviceBindingView from '../../src/views/checkin/DeviceBindingView.vue'

async function mountDBView() {
  const pinia = createPinia()
  setActivePinia(pinia)
  return mount(DeviceBindingView, { global: { plugins: [pinia] } })
}

describe('DeviceBindingView', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('renders title and about section', async () => {
    listEvents.mockResolvedValue({ data: { data: { content: [], page: 0, totalPages: 0, totalElements: 0 } } })
    const wrapper = await mountDBView()
    await flushPromises()
    expect(wrapper.text()).toContain('Device Binding Status')
    expect(wrapper.text()).toContain('About Device Binding')
    expect(wrapper.text()).toContain('DEVICE_CONFLICT')
  })

  it('renders session selector with fetched events', async () => {
    listEvents.mockResolvedValue({ data: { data: { content: [
      { id: 'e1', title: 'Session A' }
    ], page: 0, totalPages: 1, totalElements: 1 } } })
    const wrapper = await mountDBView()
    await flushPromises()
    expect(wrapper.find('#session-select').exists()).toBe(true)
    expect(wrapper.text()).toContain('Session A')
  })

  it('loads roster and conflicts when a session is selected', async () => {
    listEvents.mockResolvedValue({ data: { data: { content: [
      { id: 'e1', title: 'Session A' }
    ], page: 0, totalPages: 1, totalElements: 1 } } })
    getRoster.mockResolvedValue({ data: { data: [{ userId: 'u1', deviceToken: 'dev-1', checkedInAt: '2026-04-01T10:00:00Z' }] } })
    getConflicts.mockResolvedValue({ data: { data: [] } })
    const wrapper = await mountDBView()
    await flushPromises()
    await wrapper.find('#session-select').setValue('e1')
    await flushPromises()
    expect(getRoster).toHaveBeenCalledWith('e1')
    expect(getConflicts).toHaveBeenCalledWith('e1')
    expect(wrapper.text()).toContain('dev-1')
    expect(wrapper.text()).toContain('BOUND')
  })

  it('marks devices with CONFLICT when conflicts exist for same userId', async () => {
    listEvents.mockResolvedValue({ data: { data: { content: [
      { id: 'e1', title: 'Session A' }
    ], page: 0, totalPages: 1, totalElements: 1 } } })
    getRoster.mockResolvedValue({ data: { data: [{ userId: 'u1', deviceToken: 'dev-1' }] } })
    getConflicts.mockResolvedValue({ data: { data: [
      { id: 'c1', userId: 'u1', conflictType: 'DEVICE_CONFLICT', deviceToken: 'dev-2' }
    ] } })
    const wrapper = await mountDBView()
    await flushPromises()
    await wrapper.find('#session-select').setValue('e1')
    await flushPromises()
    expect(wrapper.text()).toContain('CONFLICT')
  })

  it('shows empty devices state when no device tokens in roster', async () => {
    listEvents.mockResolvedValue({ data: { data: { content: [
      { id: 'e1', title: 'Session A' }
    ], page: 0, totalPages: 1, totalElements: 1 } } })
    getRoster.mockResolvedValue({ data: { data: [{ userId: 'u1' }] } })
    getConflicts.mockResolvedValue({ data: { data: [] } })
    const wrapper = await mountDBView()
    await flushPromises()
    await wrapper.find('#session-select').setValue('e1')
    await flushPromises()
    expect(wrapper.text()).toContain('No device bindings recorded')
  })
})
