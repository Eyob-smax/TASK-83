package com.eventops.common.exception;

import com.eventops.common.dto.ApiResponse;
import com.eventops.common.dto.ApiResponse.ApiError;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Global exception handler for all REST controllers.
 *
 * <p>Catches and normalizes exceptions into the standard {@link ApiResponse}
 * envelope so that no raw stack traces or sensitive data are ever exposed to
 * the frontend client.</p>
 *
 * <p>Client errors (4xx) are logged at WARN level; server errors (5xx) are
 * logged at ERROR level. Request bodies are never included in log messages
 * to avoid leaking sensitive data.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Bean-validation failures from {@code @Valid} annotated request bodies.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError(fe.getField(), fe.getCode(), fe.getDefaultMessage()))
                .collect(Collectors.toList());

        log.warn("Validation failed: {} field error(s)", fieldErrors.size());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Validation failed", fieldErrors));
    }

    /**
     * Constraint violations from {@code @Validated} path/query parameters.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleConstraintViolation(ConstraintViolationException ex) {
        List<ApiError> errors = ex.getConstraintViolations().stream()
                .map(cv -> new ApiError(
                        cv.getPropertyPath().toString(),
                        "ConstraintViolation",
                        cv.getMessage()))
                .collect(Collectors.toList());

        log.warn("Constraint violation: {} violation(s)", errors.size());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Validation failed", errors));
    }

    /**
     * Malformed or unreadable JSON request bodies.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body received");
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Malformed request body", null));
    }

    /**
     * Spring Security authentication failures.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<?>> handleAuthentication(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Authentication required", null));
    }

    /**
     * Spring Security authorization failures.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<?>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied", null));
    }

    /**
     * Database unique-constraint and foreign-key violations.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleDataIntegrity(DataIntegrityViolationException ex) {
        String message = extractConflictMessage(ex);
        log.warn("Data integrity violation: {}", message);
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(message, null));
    }

    /**
     * Explicit {@link ResponseStatusException} thrown by controllers or filters.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<?>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        if (status.is4xxClientError()) {
            log.warn("Response status exception: {} {}", status.value(), ex.getReason());
        } else {
            log.error("Response status exception: {} {}", status.value(), ex.getReason());
        }
        String reason = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return ResponseEntity
                .status(status)
                .body(ApiResponse.error(reason, null));
    }

    /**
     * Typed business-rule exceptions thrown by service-layer code.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<?>> handleBusiness(BusinessException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getHttpStatus());
        if (status.is4xxClientError()) {
            log.warn("Business error [{}]: {}", ex.getErrorCode(), ex.getMessage());
        } else {
            log.error("Business error [{}]: {}", ex.getErrorCode(), ex.getMessage());
        }

        ApiError error = new ApiError(null, ex.getErrorCode(), ex.getMessage());
        if (ex.getConflictType() != null) {
            error.setCode(ex.getConflictType().name());
        }
        return ResponseEntity
                .status(status)
                .body(ApiResponse.error(ex.getMessage(), List.of(error)));
    }

    /**
     * Catch-all for any unhandled exception. Never exposes stack traces.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGeneric(Exception ex) {
        log.error("Unhandled exception: {}", ex.getClass().getName(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Internal server error", null));
    }

    /**
     * Attempts to extract a user-friendly conflict message from a
     * {@link DataIntegrityViolationException}.
     */
    private String extractConflictMessage(DataIntegrityViolationException ex) {
        String rootMessage = ex.getMostSpecificCause().getMessage();
        if (rootMessage != null && rootMessage.toLowerCase().contains("duplicate")) {
            return "A record with the given unique value already exists";
        }
        if (rootMessage != null && rootMessage.toLowerCase().contains("foreign key")) {
            return "Operation references a record that does not exist or cannot be removed";
        }
        return "Data conflict occurred";
    }
}
