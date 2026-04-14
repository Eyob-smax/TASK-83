package com.eventops.service.notification;

import com.eventops.domain.notification.NotificationType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Canonical list of notification templates that must exist for core business flows.
 */
public final class RequiredNotificationTemplates {

    private static final Set<NotificationType> REQUIRED_TYPES = Collections.unmodifiableSet(EnumSet.of(
            NotificationType.REGISTRATION_CONFIRMATION,
            NotificationType.WAITLIST_PROMOTION,
            NotificationType.WAITLIST_EXPIRED,
            NotificationType.CHECKIN_EXCEPTION,
            NotificationType.CHECKIN_DEVICE_WARNING,
            NotificationType.FINANCE_POSTING_RESULT
    ));

    private RequiredNotificationTemplates() {
    }

    public static Set<NotificationType> all() {
        return REQUIRED_TYPES;
    }

    public static boolean isRequired(NotificationType type) {
        return REQUIRED_TYPES.contains(type);
    }
}
