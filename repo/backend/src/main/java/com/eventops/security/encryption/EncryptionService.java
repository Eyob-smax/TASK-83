package com.eventops.security.encryption;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Provides AES-256-GCM encryption and decryption for sensitive data at rest.
 *
 * <p>The encrypt method prepends a random 12-byte IV to the ciphertext so that
 * each encryption produces a unique output even for identical plaintext. The
 * decrypt method extracts the IV before decrypting.</p>
 *
 * <p>This service never logs plaintext values or the encryption key.</p>
 */
@Service
@EnableConfigurationProperties(EncryptionProperties.class)
public class EncryptionService {

    private static final Logger log = LoggerFactory.getLogger(EncryptionService.class);

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final String DEFAULT_PLACEHOLDER = "CHANGE_ME_IN_PRODUCTION";

    private final EncryptionProperties properties;
    private final SecureRandom secureRandom;
    private SecretKeySpec secretKeySpec;

    public EncryptionService(EncryptionProperties properties) {
        this.properties = properties;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Validates that the encryption key is not the default placeholder and
     * derives the AES key on startup.
     */
    @PostConstruct
    void init() {
        if (properties.getSecretKey() == null
                || properties.getSecretKey().isBlank()
                || DEFAULT_PLACEHOLDER.equals(properties.getSecretKey())) {
            throw new IllegalStateException(
                    "Encryption secret key must be configured and must not be the default placeholder. "
                            + "Set the ENCRYPTION_SECRET_KEY environment variable.");
        }
        this.secretKeySpec = deriveKey(properties.getSecretKey());
        log.info("Encryption service initialised — algorithm={}, key-size={}",
                properties.getAlgorithm(), properties.getKeySize());
    }

    /**
     * Encrypts the given plaintext using AES/GCM/NoPadding.
     *
     * @param plaintext the text to encrypt
     * @return a byte array containing the 12-byte IV followed by the ciphertext
     *         (including the GCM authentication tag)
     */
    public byte[] encrypt(String plaintext) {
        try {
            log.debug("Performing encrypt operation");

            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(properties.getTransformation());
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext
            byte[] result = new byte[GCM_IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, GCM_IV_LENGTH);
            System.arraycopy(ciphertext, 0, result, GCM_IV_LENGTH, ciphertext.length);

            return result;
        } catch (Exception ex) {
            throw new EncryptionException("Encryption failed", ex);
        }
    }

    /**
     * Decrypts the given cipher data (IV + ciphertext) back to plaintext.
     *
     * @param cipherData the byte array with the first 12 bytes as the IV
     * @return the decrypted plaintext string
     */
    public String decrypt(byte[] cipherData) {
        try {
            log.debug("Performing decrypt operation");

            if (cipherData == null || cipherData.length <= GCM_IV_LENGTH) {
                throw new IllegalArgumentException("Cipher data is too short or null");
            }

            byte[] iv = Arrays.copyOfRange(cipherData, 0, GCM_IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(cipherData, GCM_IV_LENGTH, cipherData.length);

            Cipher cipher = Cipher.getInstance(properties.getTransformation());
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, gcmSpec);

            byte[] plainBytes = cipher.doFinal(ciphertext);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new EncryptionException("Decryption failed", ex);
        }
    }

    /**
     * Returns a masked version of the value, showing only the last 4 characters.
     *
     * @param value the value to mask
     * @return masked string, or null if value is null
     */
    public String mask(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() <= 4) {
            return "****";
        }
        return "****" + value.substring(value.length() - 4);
    }

    /**
     * Derives a 256-bit AES key from the configured secret string by hashing
     * it with SHA-256.
     */
    private SecretKeySpec deriveKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, properties.getAlgorithm());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }

    /**
     * Runtime exception wrapper for encryption/decryption failures.
     */
    public static class EncryptionException extends RuntimeException {
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
