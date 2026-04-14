package com.eventops.audit.logging;

/**
 * Immutable representation of a single field-level change for audit logging.
 *
 * @param fieldName the name of the field that changed (never {@code null})
 * @param oldValue  the previous value ({@code null} when the field was absent before)
 * @param newValue  the current value ({@code null} when the field was removed)
 */
public record FieldChange(
        String fieldName,
        String oldValue,
        String newValue
) {

    /**
     * Compact constructor — enforces that fieldName is never null.
     */
    public FieldChange {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName must not be null or blank");
        }
    }
}
