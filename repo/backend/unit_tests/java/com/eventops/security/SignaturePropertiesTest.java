package com.eventops.security;

import com.eventops.security.signature.SignatureProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SignaturePropertiesTest {

    @Test
    void defaultAlgorithm_isHmacSHA256() {
        SignatureProperties props = new SignatureProperties();
        assertEquals("HmacSHA256", props.getAlgorithm());
    }

    @Test
    void defaultMaxAgeSeconds_is300() {
        SignatureProperties props = new SignatureProperties();
        assertEquals(300, props.getMaxAgeSeconds());
    }

    @Test
    void defaultEnabled_isFalse() {
        SignatureProperties props = new SignatureProperties();
        assertFalse(props.isEnabled());
    }

    @Test
    void setEnabled_roundTrip() {
        SignatureProperties props = new SignatureProperties();
        props.setEnabled(true);
        assertTrue(props.isEnabled());
    }

    @Test
    void algorithmSetter_roundTrip() {
        SignatureProperties props = new SignatureProperties();
        props.setAlgorithm("HmacSHA512");
        assertEquals("HmacSHA512", props.getAlgorithm());
    }

    @Test
    void maxAgeSecondsSetter_roundTrip() {
        SignatureProperties props = new SignatureProperties();
        props.setMaxAgeSeconds(600);
        assertEquals(600, props.getMaxAgeSeconds());
    }

    @Test
    void secretKeySetter_roundTrip() {
        SignatureProperties props = new SignatureProperties();
        assertNull(props.getSecretKey());
        props.setSecretKey("very-secret");
        assertEquals("very-secret", props.getSecretKey());
    }
}
