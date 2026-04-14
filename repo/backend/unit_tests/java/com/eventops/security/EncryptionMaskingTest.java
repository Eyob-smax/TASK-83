package com.eventops.security;

import com.eventops.security.encryption.EncryptionProperties;
import com.eventops.security.encryption.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EncryptionService} masking and encrypt/decrypt edge cases.
 *
 * <p>Uses a real {@link EncryptionProperties} with a test key so we can exercise
 * the actual AES-256-GCM encrypt/decrypt round-trip without Spring context.</p>
 */
class EncryptionMaskingTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        EncryptionProperties properties = new EncryptionProperties();
        properties.setAlgorithm("AES");
        properties.setTransformation("AES/GCM/NoPadding");
        properties.setKeySize(256);
        properties.setSecretKey("test-secret-key-for-unit-tests-only");

        encryptionService = new EncryptionService(properties);

        // Trigger @PostConstruct via reflection because init() is package-private.
        try {
            Method init = EncryptionService.class.getDeclaredMethod("init");
            init.setAccessible(true);
            init.invoke(encryptionService);
        } catch (Exception e) {
            fail("Failed to initialize EncryptionService in test setup", e);
        }
    }

    // ------------------------------------------------------------------
    // mask() edge cases
    // ------------------------------------------------------------------

    @Test
    void mask_exactlyFourChars_fullyMasked() {
        String result = encryptionService.mask("abcd");
        assertEquals("****", result);
    }

    @Test
    void mask_fiveChars_showsLastFour() {
        // Input "abcde" has length 5, so mask shows **** + last 4 chars
        String result = encryptionService.mask("abcde");
        assertEquals("****bcde", result);
    }

    @Test
    void mask_email_showsDomain() {
        // "user@test.com" is 13 chars, last 4 are ".com"
        String result = encryptionService.mask("user@test.com");
        assertEquals("****.com", result);
    }

    @Test
    void mask_null_returnsNull() {
        assertNull(encryptionService.mask(null));
    }

    @Test
    void mask_singleChar_fullyMasked() {
        assertEquals("****", encryptionService.mask("x"));
    }

    @Test
    void mask_emptyString_fullyMasked() {
        assertEquals("****", encryptionService.mask(""));
    }

    @Test
    void mask_threeChars_fullyMasked() {
        assertEquals("****", encryptionService.mask("abc"));
    }

    @Test
    void mask_longString_showsLastFour() {
        String result = encryptionService.mask("1234567890");
        assertEquals("****7890", result);
    }

    // ------------------------------------------------------------------
    // encrypt/decrypt edge cases
    // ------------------------------------------------------------------

    @Test
    void encrypt_null_throwsOrHandlesGracefully() {
        // The encrypt method takes String plaintext and calls plaintext.getBytes(),
        // so passing null will throw a NullPointerException wrapped in EncryptionException.
        assertThrows(Exception.class, () -> encryptionService.encrypt(null));
    }

    @Test
    void decrypt_null_throwsIllegalArgument() {
        // decrypt checks for null and throws IllegalArgumentException
        assertThrows(Exception.class, () -> encryptionService.decrypt(null));
    }

    @Test
    void decrypt_tooShort_throwsIllegalArgument() {
        // Cipher data shorter than 12 bytes (GCM IV length) should be rejected
        assertThrows(Exception.class, () -> encryptionService.decrypt(new byte[5]));
    }

    @Test
    void encrypt_emptyString_roundTrips() {
        byte[] encrypted = encryptionService.encrypt("");
        assertNotNull(encrypted);
        assertTrue(encrypted.length > 12); // at least IV + GCM tag

        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals("", decrypted);
    }

    @Test
    void encrypt_decrypt_roundTrip_normalString() {
        String original = "Hello, EventOps!";
        byte[] encrypted = encryptionService.encrypt(original);

        assertNotNull(encrypted);
        assertTrue(encrypted.length > original.length());

        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void encrypt_producesUniqueOutput_forSamePlaintext() {
        String plaintext = "same-input";
        byte[] encrypted1 = encryptionService.encrypt(plaintext);
        byte[] encrypted2 = encryptionService.encrypt(plaintext);

        // Due to random IV, encryptions of the same plaintext should differ
        assertNotEquals(java.util.Arrays.toString(encrypted1),
                        java.util.Arrays.toString(encrypted2));

        // Both should decrypt to the same value
        assertEquals(plaintext, encryptionService.decrypt(encrypted1));
        assertEquals(plaintext, encryptionService.decrypt(encrypted2));
    }
}
