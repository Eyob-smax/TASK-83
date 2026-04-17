package com.eventops.security;

import com.eventops.security.encryption.EncryptionProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionPropertiesTest {

    @Test
    void defaultConstructor_fieldsStartNullOrZero() {
        EncryptionProperties props = new EncryptionProperties();
        assertNull(props.getAlgorithm());
        assertNull(props.getTransformation());
        assertEquals(0, props.getKeySize());
        assertNull(props.getSecretKey());
    }

    @Test
    void algorithmSetterAndGetter_roundTrip() {
        EncryptionProperties props = new EncryptionProperties();
        props.setAlgorithm("AES");
        assertEquals("AES", props.getAlgorithm());
    }

    @Test
    void transformationSetterAndGetter_roundTrip() {
        EncryptionProperties props = new EncryptionProperties();
        props.setTransformation("AES/GCM/NoPadding");
        assertEquals("AES/GCM/NoPadding", props.getTransformation());
    }

    @Test
    void keySizeSetterAndGetter_roundTrip() {
        EncryptionProperties props = new EncryptionProperties();
        props.setKeySize(256);
        assertEquals(256, props.getKeySize());
    }

    @Test
    void secretKeySetterAndGetter_roundTrip() {
        EncryptionProperties props = new EncryptionProperties();
        props.setSecretKey("super-secret-32-bytes-key!!");
        assertEquals("super-secret-32-bytes-key!!", props.getSecretKey());
    }
}
