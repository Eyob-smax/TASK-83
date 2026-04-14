package com.eventops.domain.event;

/**
 * Event session lifecycle states.
 */
public enum SessionStatus {
    DRAFT,
    PUBLISHED,
    OPEN_FOR_REGISTRATION,
    FULL,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
