package com.eventops.security;

import com.eventops.security.encryption.EncryptionService;
import com.eventops.security.encryption.EncryptionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionServiceTest {

    private EncryptionService service;

    @BeforeEach
    void setup() {
        EncryptionProperties props = new EncryptionProperties();
        props.setAlgorithm("AES");
        props.setTransformation("AES/GCM/NoPadding");
        props.setKeySize(256);
        props.setSecretKey("test-encryption-key-for-unit-tests");
        service = new EncryptionService(props);

        try {
            Method init = EncryptionService.class.getDeclaredMethod("init");
            init.setAccessible(true);
            init.invoke(service);
        } catch (Exception e) {
            fail("Failed to initialize EncryptionService", e);
        }
    }

    @Test
    void encryptDecrypt_roundTrip() {
        String original = "sensitive@email.com";
        byte[] encrypted = service.encrypt(original);
        assertNotNull(encrypted);
        assertTrue(encrypted.length > 0);

        String decrypted = service.decrypt(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void encrypt_producesDifferentCiphertextEachTime() {
        String original = "test data";
        byte[] enc1 = service.encrypt(original);
        byte[] enc2 = service.encrypt(original);
        assertFalse(java.util.Arrays.equals(enc1, enc2), "GCM with random IV should produce different ciphertexts");
    }

    @Test
    void mask_nullReturnsNull() {
        assertNull(service.mask(null));
    }

    @Test
    void mask_shortValueFullyMasked() {
        assertEquals("****", service.mask("abc"));
    }

    @Test
    void mask_longValueShowsLastFour() {
        String masked = service.mask("sensitive@email.com");
        assertTrue(masked.endsWith(".com"));
        assertTrue(masked.startsWith("****"));
    }
}
