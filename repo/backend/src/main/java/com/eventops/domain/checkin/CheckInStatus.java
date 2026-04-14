package com.eventops.domain.checkin;

/**
 * Check-in attempt result states.
 */
public enum CheckInStatus {
    CHECKED_IN,
    DENIED_WINDOW_CLOSED,
    DENIED_INVALID_PASSCODE,
    DENIED_DEVICE_CONFLICT,
    DENIED_DUPLICATE,
    DENIED_NOT_REGISTERED,
    CONFLICT_MULTI_DEVICE
}
