package com.eventops.audit;

import com.eventops.audit.logging.LogSanitizer;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LogSanitizerTest {

    @Test
    void sanitize_redactsPasswordValues() {
        String input = "Login attempt: password=secret123 for user john";
        String result = LogSanitizer.sanitize(input);
        assertFalse(result.contains("secret123"));
        assertTrue(result.contains("[REDACTED]"));
    }

    @Test
    void sanitize_redactsTokenValues() {
        String input = "Request with token=abc123def";
        String result = LogSanitizer.sanitize(input);
        assertFalse(result.contains("abc123def"));
    }

    @Test
    void sanitize_preservesNonSensitiveContent() {
        String input = "User john logged in successfully";
        String result = LogSanitizer.sanitize(input);
        assertEquals(input, result);
    }

    @Test
    void sanitize_handlesNull() {
        assertNull(LogSanitizer.sanitize(null));
    }

    @Test
    void sanitizeMap_redactsSensitiveKeys() {
        Map<String, Object> params = Map.of("username", "john", "password", "secret", "action", "login");
        String result = LogSanitizer.sanitizeMap(params);
        assertFalse(result.contains("secret"));
        assertTrue(result.contains("john"));
        assertTrue(result.contains("[REDACTED]"));
    }
}
