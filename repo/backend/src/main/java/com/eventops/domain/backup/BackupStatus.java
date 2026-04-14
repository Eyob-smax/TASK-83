package com.eventops.domain.backup;

/**
 * Backup job execution states.
 */
public enum BackupStatus {
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    /** Retention period elapsed; artifact deleted during cleanup. */
    EXPIRED
}
