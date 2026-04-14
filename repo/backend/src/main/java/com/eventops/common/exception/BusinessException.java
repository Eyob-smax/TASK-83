package com.eventops.common.exception;

import com.eventops.common.dto.ConflictType;

/**
 * Typed business-rule exception thrown by service-layer code.
 *
 * <p>Carries an HTTP status code, a machine-readable error code, and an
 * optional {@link ConflictType} so the global exception handler can build
 * a precise {@code ApiResponse} for the frontend.</p>
 */
public class BusinessException extends RuntimeException {

    private final int httpStatus;
    private final String errorCode;
    private final ConflictType conflictType;

    /**
     * Creates a business exception without a conflict type.
     *
     * @param message    human-readable error message
     * @param httpStatus HTTP status code (e.g. 400, 404, 422)
     * @param errorCode  machine-readable error code (e.g. "QUOTA_EXCEEDED")
     */
    public BusinessException(String message, int httpStatus, String errorCode) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.conflictType = null;
    }

    /**
     * Creates a business exception with a conflict type (typically for 409 responses).
     *
     * @param message      human-readable error message
     * @param httpStatus   HTTP status code
     * @param errorCode    machine-readable error code
     * @param conflictType the type of conflict for the frontend to handle
     */
    public BusinessException(String message, int httpStatus, String errorCode, ConflictType conflictType) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.conflictType = conflictType;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public ConflictType getConflictType() {
        return conflictType;
    }
}
