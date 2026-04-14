import { describe, it, expect, beforeEach, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";

vi.mock("../../src/api/checkin.js", () => ({
  getPasscode: vi.fn(),
  checkInAttendee: vi.fn(),
  getRoster: vi.fn(),
  getConflicts: vi.fn(),
}));

import { useCheckinStore } from "../../src/stores/checkin.js";
import { getPasscode, checkInAttendee } from "../../src/api/checkin.js";

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
});
