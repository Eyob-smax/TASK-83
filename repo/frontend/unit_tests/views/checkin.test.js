import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { mount, flushPromises } from '@vue/test-utils'

vi.mock("../../src/api/checkin.js", () => ({
  getPasscode: vi.fn(),
  checkInAttendee: vi.fn(),
  getRoster: vi.fn(),
  getConflicts: vi.fn(),
}));

vi.mock('../../src/api/events.js', () => ({
  listEvents: vi.fn(),
  getEvent: vi.fn(),
  getAvailability: vi.fn()
}))

vi.mock('@/utils/offline', () => ({
  offlineQueue: { keys: vi.fn(async () => []), getItem: vi.fn(), setItem: vi.fn(), removeItem: vi.fn() },
  rosterCache: { getItem: vi.fn(), setItem: vi.fn(), removeItem: vi.fn() }
}))
vi.mock('@/utils/resumableUpload', () => ({ uploadAttachmentResumable: vi.fn() }))

import { useCheckinStore } from "../../src/stores/checkin.js";
import { getPasscode, checkInAttendee, getRoster, getConflicts } from "../../src/api/checkin.js";
import { listEvents } from '../../src/api/events.js'
import CheckInView from '../../src/views/checkin/CheckInView.vue'

describe("check-in store behavior", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it("fetchPasscode hydrates current passcode and remaining countdown", async () => {
    getPasscode.mockResolvedValue({
      data: {
        data: {
          passcode: "482917",
          remainingSeconds: 45,
        },
      },
    });

    const store = useCheckinStore();
    await store.fetchPasscode("session-1");

    expect(store.currentPasscode).toBe("482917");
    expect(store.passcodeCountdown).toBe(45);
  });

  it("performCheckIn stores conflict detail for conflict errors", async () => {
    checkInAttendee.mockRejectedValue({
      response: {
        data: {
          message: "Device conflict",
          errors: [{ code: "DEVICE_CONFLICT" }],
        },
      },
    });

    const store = useCheckinStore();

    await expect(
      store.performCheckIn("session-1", {
        userId: "attendee-1",
        passcode: "123456",
      }),
    ).rejects.toBeTruthy();

    expect(store.conflictDetail).toEqual({
      code: "DEVICE_CONFLICT",
      message: "Device conflict",
    });
    expect(store.error).toBeNull();
  });

  it("performCheckIn sets generic error for non-conflict failures", async () => {
    checkInAttendee.mockRejectedValue({
      response: {
        data: {
          message: "Check-in window is not open",
          errors: [{ code: "WINDOW_CLOSED" }],
        },
      },
    });

    const store = useCheckinStore();

    await expect(
      store.performCheckIn("session-1", {
        userId: "attendee-1",
        passcode: "123456",
      }),
    ).rejects.toBeTruthy();

    expect(store.conflictDetail).toBeNull();
    expect(store.error).toBe("Check-in window is not open");
  });

  it('performCheckIn success returns result and clears conflict', async () => {
    checkInAttendee.mockResolvedValue({ data: { data: { status: 'CHECKED_IN' } } })
    const store = useCheckinStore()
    const result = await store.performCheckIn('s1', { userId: 'u1', passcode: '111111' })
    expect(result.status).toBe('CHECKED_IN')
    expect(store.conflictDetail).toBeNull()
  })

  it('fetchRoster populates roster from API', async () => {
    getRoster.mockResolvedValue({ data: { data: [{ userId: 'u1', name: 'Alice' }] } })
    const store = useCheckinStore()
    await store.fetchRoster('s1')
    expect(store.roster).toHaveLength(1)
  })

  it('clearConflict resets conflictDetail', async () => {
    const store = useCheckinStore()
    store.conflictDetail = { code: 'X' }
    store.clearConflict()
    expect(store.conflictDetail).toBeNull()
  })

  it('fetchConflicts swallows errors silently', async () => {
    getConflicts.mockRejectedValue(new Error('boom'))
    const store = useCheckinStore()
    await expect(store.fetchConflicts('s1')).resolves.toBeUndefined()
  })
})

// --------------------------------------------------------------------------
// CheckInView component tests
// --------------------------------------------------------------------------

async function mountCheckinView() {
  const pinia = createPinia()
  setActivePinia(pinia)
  return mount(CheckInView, { global: { plugins: [pinia] } })
}

describe('CheckInView', () => {
  beforeEach(() => { vi.clearAllMocks(); vi.useFakeTimers() })
  afterEach(() => { vi.useRealTimers() })

  it('renders session selector after events load', async () => {
    listEvents.mockResolvedValue({ data: { data: { content: [
      { id: 'e1', title: 'Morning', maxCapacity: 10, remainingSeats: 5 }
    ], page: 0, totalPages: 1, totalElements: 1 } } })
    const wrapper = await mountCheckinView()
    await flushPromises()
    expect(wrapper.find('#session-select').exists()).toBe(true)
    expect(wrapper.text()).toContain('Morning')
  })

  it('selecting a session fetches passcode and roster', async () => {
    listEvents.mockResolvedValue({ data: { data: { content: [
      { id: 'e1', title: 'Morning', maxCapacity: 10, remainingSeats: 5 }
    ], page: 0, totalPages: 1, totalElements: 1 } } })
    getPasscode.mockResolvedValue({ data: { data: { passcode: '123456', remainingSeconds: 45 } } })
    getRoster.mockResolvedValue({ data: { data: [] } })
    getConflicts.mockResolvedValue({ data: { data: [] } })
    const wrapper = await mountCheckinView()
    await flushPromises()
    await wrapper.find('#session-select').setValue('e1')
    await flushPromises()
    expect(getPasscode).toHaveBeenCalledWith('e1')
    expect(getRoster).toHaveBeenCalledWith('e1')
  })

  it('displays passcode when fetched', async () => {
    listEvents.mockResolvedValue({ data: { data: { content: [
      { id: 'e1', title: 'Morning', maxCapacity: 10, remainingSeats: 5 }
    ], page: 0, totalPages: 1, totalElements: 1 } } })
    getPasscode.mockResolvedValue({ data: { data: { passcode: '123456', remainingSeconds: 45 } } })
    getRoster.mockResolvedValue({ data: { data: [] } })
    getConflicts.mockResolvedValue({ data: { data: [] } })
    const wrapper = await mountCheckinView()
    await flushPromises()
    await wrapper.find('#session-select').setValue('e1')
    await flushPromises()
    expect(wrapper.text()).toContain('123456')
    expect(wrapper.text()).toContain('Expires in')
  })

  it('check-in form submit calls checkInAttendee', async () => {
    listEvents.mockResolvedValue({ data: { data: { content: [
      { id: 'e1', title: 'Morning', maxCapacity: 10, remainingSeats: 5 }
    ], page: 0, totalPages: 1, totalElements: 1 } } })
    getPasscode.mockResolvedValue({ data: { data: { passcode: '123456', remainingSeconds: 45 } } })
    getRoster.mockResolvedValue({ data: { data: [] } })
    getConflicts.mockResolvedValue({ data: { data: [] } })
    checkInAttendee.mockResolvedValue({ data: { data: { status: 'CHECKED_IN' } } })
    const wrapper = await mountCheckinView()
    await flushPromises()
    await wrapper.find('#session-select').setValue('e1')
    await flushPromises()
    await wrapper.find('#attendee-id').setValue('user-1')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
    expect(checkInAttendee).toHaveBeenCalled()
  })
})
