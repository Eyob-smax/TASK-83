package com.eventops.common.validation;

import com.eventops.common.exception.BusinessException;

import java.time.LocalDateTime;

/**
 * Static utility methods for imperative validation in service-layer code.
 *
 * <p>Each method throws a {@link BusinessException} with HTTP 400 and a
 * descriptive message when the precondition is not met.</p>
 */
public final class ValidationHelper {

    private static final int BAD_REQUEST = 400;
    private static final String VALIDATION_ERROR = "VALIDATION_ERROR";

    private ValidationHelper() {
        // Utility class — no instantiation
    }

    /**
     * Asserts that the given string is not null, empty, or blank.
     *
     * @param value     the string to check
     * @param fieldName the field name for the error message
     * @throws BusinessException with status 400 if the value is blank
     */
    public static void requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(
                    fieldName + " must not be blank",
                    BAD_REQUEST,
                    VALIDATION_ERROR);
        }
    }

    /**
     * Asserts that the given integer is strictly positive.
     *
     * @param value     the integer to check
     * @param fieldName the field name for the error message
     * @throws BusinessException with status 400 if the value is zero or negative
     */
    public static void requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new BusinessException(
                    fieldName + " must be positive",
                    BAD_REQUEST,
                    VALIDATION_ERROR);
        }
    }

    /**
     * Asserts that the given date-time is in the future.
     *
     * @param dateTime  the date-time to check
     * @param fieldName the field name for the error message
     * @throws BusinessException with status 400 if the date-time is null or in the past
     */
    public static void requireFuture(LocalDateTime dateTime, String fieldName) {
        if (dateTime == null || !dateTime.isAfter(LocalDateTime.now())) {
            throw new BusinessException(
                    fieldName + " must be in the future",
                    BAD_REQUEST,
                    VALIDATION_ERROR);
        }
    }

    /**
     * Asserts that the given object is not null.
     *
     * @param value     the object to check
     * @param fieldName the field name for the error message
     * @throws BusinessException with status 400 if the value is null
     */
    public static void requireNotNull(Object value, String fieldName) {
        if (value == null) {
            throw new BusinessException(
                    fieldName + " must not be null",
                    BAD_REQUEST,
                    VALIDATION_ERROR);
        }
    }
}
