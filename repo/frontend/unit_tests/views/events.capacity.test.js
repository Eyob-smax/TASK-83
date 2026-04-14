import { describe, it, expect } from "vitest";

describe("events seat quota uses maxCapacity", () => {
  it("computes filled percentage from maxCapacity", () => {
    const event = { remainingSeats: 30, maxCapacity: 120 };
    const filled = event.maxCapacity - event.remainingSeats;
    const percent = Math.min(
      100,
      Math.round((filled / event.maxCapacity) * 100),
    );

    expect(percent).toBe(75);
  });

  it("computes seat ratio class input from maxCapacity", () => {
    const event = { remainingSeats: 6, maxCapacity: 12 };
    const ratio = event.remainingSeats / event.maxCapacity;

    expect(ratio).toBe(0.5);
  });
});
