package com.eventops.domain.notification;

/**
 * Notification send attempt states for the retry queue.
 */
public enum SendStatus {
    PENDING,
    DELIVERED,
    FAILED_RETRYING,
    PERMANENTLY_FAILED
}
