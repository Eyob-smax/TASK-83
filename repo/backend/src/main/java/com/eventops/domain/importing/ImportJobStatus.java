package com.eventops.domain.importing;

/**
 * Import crawl job lifecycle states.
 */
public enum ImportJobStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CIRCUIT_BROKEN
}
