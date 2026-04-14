package com.eventops.security;

import com.eventops.security.auth.PasswordService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordServiceTest {

    private final PasswordService passwordService = new PasswordService();

    @Test
    void encode_producesNonNullHash() {
        String hash = passwordService.encode("testPassword123");
        assertNotNull(hash);
        assertFalse(hash.isEmpty());
    }

    @Test
    void encode_producesDifferentHashesForSameInput() {
        String hash1 = passwordService.encode("testPassword123");
        String hash2 = passwordService.encode("testPassword123");
        assertNotEquals(hash1, hash2, "BCrypt should produce different hashes due to random salt");
    }

    @Test
    void matches_returnsTrueForCorrectPassword() {
        String hash = passwordService.encode("myPassword");
        assertTrue(passwordService.matches("myPassword", hash));
    }

    @Test
    void matches_returnsFalseForWrongPassword() {
        String hash = passwordService.encode("myPassword");
        assertFalse(passwordService.matches("wrongPassword", hash));
    }

    @Test
    void encode_hashStartsWithBcryptPrefix() {
        String hash = passwordService.encode("test");
        assertTrue(hash.startsWith("$2a$") || hash.startsWith("$2b$"), "Should be BCrypt hash");
    }
}
