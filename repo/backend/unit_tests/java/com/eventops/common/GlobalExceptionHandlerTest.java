package com.eventops.common;

import com.eventops.common.dto.ApiResponse;
import com.eventops.common.dto.ConflictType;
import com.eventops.common.exception.BusinessException;
import com.eventops.common.exception.GlobalExceptionHandler;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleValidation_returnsBadRequestWithFieldErrors() throws NoSuchMethodException {
        BindingResult br = new BeanPropertyBindingResult(new Object(), "obj");
        br.addError(new FieldError("obj", "username", "must not be blank"));
        MethodParameter mp = mock(MethodParameter.class);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(mp, br);

        ResponseEntity<ApiResponse<?>> response = handler.handleValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Validation failed", response.getBody().getMessage());
        assertEquals(1, response.getBody().getErrors().size());
    }

    @Test
    void handleConstraintViolation_returnsBadRequest() {
        ConstraintViolation<Object> cv = mock(ConstraintViolation.class);
        jakarta.validation.Path path = mock(jakarta.validation.Path.class);
        when(path.toString()).thenReturn("field");
        when(cv.getPropertyPath()).thenReturn(path);
        when(cv.getMessage()).thenReturn("bad value");
        ConstraintViolationException ex = new ConstraintViolationException("msg", Set.of(cv));

        ResponseEntity<ApiResponse<?>> response = handler.handleConstraintViolation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(1, response.getBody().getErrors().size());
    }

    @Test
    void handleNotReadable_returnsBadRequest() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("bad json");

        ResponseEntity<ApiResponse<?>> response = handler.handleNotReadable(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Malformed request body", response.getBody().getMessage());
    }

    @Test
    void handleAuthentication_returns401() {
        ResponseEntity<ApiResponse<?>> response = handler.handleAuthentication(new BadCredentialsException("bad"));
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Authentication required", response.getBody().getMessage());
    }

    @Test
    void handleAccessDenied_returns403() {
        ResponseEntity<ApiResponse<?>> response = handler.handleAccessDenied(new AccessDeniedException("nope"));
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Access denied", response.getBody().getMessage());
    }

    @Test
    void handleDataIntegrity_detectsDuplicate() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "wrap", new RuntimeException("Duplicate entry 'x' for key 'PRIMARY'"));
        ResponseEntity<ApiResponse<?>> response = handler.handleDataIntegrity(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().getMessage().contains("already exists"));
    }

    @Test
    void handleDataIntegrity_detectsForeignKey() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "wrap", new RuntimeException("foreign key constraint fails"));
        ResponseEntity<ApiResponse<?>> response = handler.handleDataIntegrity(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().getMessage().contains("references"));
    }

    @Test
    void handleDataIntegrity_genericMessage() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "wrap", new RuntimeException("some other db error"));
        ResponseEntity<ApiResponse<?>> response = handler.handleDataIntegrity(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Data conflict occurred", response.getBody().getMessage());
    }

    @Test
    void handleResponseStatus_4xxUsesWarnLog() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "No such thing");
        ResponseEntity<ApiResponse<?>> response = handler.handleResponseStatus(ex);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("No such thing", response.getBody().getMessage());
    }

    @Test
    void handleResponseStatus_5xxUsesErrorLog() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, null);
        ResponseEntity<ApiResponse<?>> response = handler.handleResponseStatus(ex);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        // Fallback reason phrase kicks in when reason is null
        assertNotNull(response.getBody().getMessage());
    }

    @Test
    void handleBusiness_4xxReturnsGivenStatus() {
        BusinessException ex = new BusinessException("Period closed", 422, "PERIOD_CLOSED");
        ResponseEntity<ApiResponse<?>> response = handler.handleBusiness(ex);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals("Period closed", response.getBody().getMessage());
        assertEquals("PERIOD_CLOSED", response.getBody().getErrors().get(0).getCode());
    }

    @Test
    void handleBusiness_withConflictType_usesConflictCode() {
        BusinessException ex = new BusinessException("Dup", 409, "ORIG_CODE", ConflictType.DUPLICATE_REGISTRATION);
        ResponseEntity<ApiResponse<?>> response = handler.handleBusiness(ex);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("DUPLICATE_REGISTRATION", response.getBody().getErrors().get(0).getCode());
    }

    @Test
    void handleBusiness_5xxUsesErrorLog() {
        BusinessException ex = new BusinessException("boom", 500, "SRV_ERR");
        ResponseEntity<ApiResponse<?>> response = handler.handleBusiness(ex);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void handleGeneric_returns500() {
        ResponseEntity<ApiResponse<?>> response = handler.handleGeneric(new RuntimeException("ka-boom"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Internal server error", response.getBody().getMessage());
    }
}
