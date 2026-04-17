package com.eventops.common;

import com.eventops.common.exception.BusinessException;
import com.eventops.common.validation.ValidationHelper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ValidationHelperTest {

    @Test
    void requireNotBlank_passesForNonBlankString() {
        assertDoesNotThrow(() -> ValidationHelper.requireNotBlank("hello", "field"));
    }

    @Test
    void requireNotBlank_throwsForNull() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ValidationHelper.requireNotBlank(null, "username"));
        assertEquals(400, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("username"));
    }

    @Test
    void requireNotBlank_throwsForEmpty() {
        assertThrows(BusinessException.class, () -> ValidationHelper.requireNotBlank("", "f"));
    }

    @Test
    void requireNotBlank_throwsForWhitespaceOnly() {
        assertThrows(BusinessException.class, () -> ValidationHelper.requireNotBlank("   ", "f"));
    }

    @Test
    void requirePositive_passesForOne() {
        assertDoesNotThrow(() -> ValidationHelper.requirePositive(1, "f"));
    }

    @Test
    void requirePositive_throwsForZero() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ValidationHelper.requirePositive(0, "capacity"));
        assertTrue(ex.getMessage().contains("capacity"));
    }

    @Test
    void requirePositive_throwsForNegative() {
        assertThrows(BusinessException.class, () -> ValidationHelper.requirePositive(-5, "f"));
    }

    @Test
    void requireFuture_passesForFutureDate() {
        assertDoesNotThrow(() ->
                ValidationHelper.requireFuture(LocalDateTime.now().plusDays(1), "f"));
    }

    @Test
    void requireFuture_throwsForNull() {
        assertThrows(BusinessException.class, () -> ValidationHelper.requireFuture(null, "f"));
    }

    @Test
    void requireFuture_throwsForPastDate() {
        assertThrows(BusinessException.class,
                () -> ValidationHelper.requireFuture(LocalDateTime.now().minusDays(1), "f"));
    }

    @Test
    void requireNotNull_passesForNonNull() {
        assertDoesNotThrow(() -> ValidationHelper.requireNotNull("x", "f"));
        assertDoesNotThrow(() -> ValidationHelper.requireNotNull(new Object(), "f"));
    }

    @Test
    void requireNotNull_throwsForNull() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ValidationHelper.requireNotNull(null, "body"));
        assertEquals(400, ex.getHttpStatus());
        assertEquals("VALIDATION_ERROR", ex.getErrorCode());
    }
}
