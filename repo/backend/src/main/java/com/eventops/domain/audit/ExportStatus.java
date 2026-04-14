package com.eventops.domain.audit;

/**
 * Export job lifecycle states.
 */
public enum ExportStatus {
    PENDING,
    GENERATING,
    COMPLETED,
    FAILED,
    DENIED
}
