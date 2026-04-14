package com.eventops.config;

import java.time.LocalTime;

/**
 * Application-wide constants traceable to prompt requirements.
 */
public final class AppConstants {

    private AppConstants() {}

    // --- Check-in ---
    public static final int PASSCODE_LENGTH = 6;
    public static final int PASSCODE_MAX_VALUE = 1_000_000;
    public static final long PASSCODE_ROTATION_INTERVAL_MS = 60_000;
    public static final int PASSCODE_EXPIRY_SECONDS = 60;
    public static final int DEFAULT_CHECKIN_WINDOW_BEFORE_MINUTES = 30;
    public static final int DEFAULT_CHECKIN_WINDOW_AFTER_MINUTES = 15;

    // --- Notifications ---
    public static final int NOTIFICATION_MAX_ATTEMPTS = 5;
    public static final long NOTIFICATION_INITIAL_BACKOFF_SECONDS = 12;
    public static final long NOTIFICATION_BACKOFF_MAX_SECONDS = 600;
    public static final long NOTIFICATION_RETRY_INTERVAL_MS = 30_000;
    public static final LocalTime DEFAULT_DND_START = LocalTime.of(21, 0);
    public static final LocalTime DEFAULT_DND_END = LocalTime.of(7, 0);

    // --- Waitlist ---
    public static final long WAITLIST_PROMOTION_INTERVAL_MS = 300_000;

    // --- Import ---
    public static final int IMPORT_DEFAULT_CONCURRENCY_CAP = 3;
    public static final int IMPORT_DEFAULT_TIMEOUT_SECONDS = 30;
    public static final int IMPORT_CIRCUIT_BREAKER_THRESHOLD = 10;

    // --- Rate Limiting ---
    public static final int DEFAULT_RATE_LIMIT_RPM = 60;

    // --- Login Throttle ---
    public static final int LOGIN_MAX_ATTEMPTS = 5;
    public static final int LOGIN_LOCKOUT_MINUTES = 15;

    // --- Backup ---
    public static final int BACKUP_RETENTION_DAYS = 30;
}
