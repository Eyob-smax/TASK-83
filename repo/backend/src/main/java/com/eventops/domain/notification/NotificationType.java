package com.eventops.domain.notification;

/**
 * Notification category types matching prompt-required triggers.
 */
public enum NotificationType {
    REGISTRATION_CONFIRMATION,
    WAITLIST_PROMOTION,
    WAITLIST_EXPIRED,
    CHECKIN_EXCEPTION,
    CHECKIN_DEVICE_WARNING,
    FINANCE_POSTING_RESULT,
    IMPORT_COMPLETED,
    IMPORT_FAILED,
    SYSTEM_ALERT
}
