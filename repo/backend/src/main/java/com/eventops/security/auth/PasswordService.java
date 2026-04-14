package com.eventops.security.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around {@link BCryptPasswordEncoder} (strength 12) for
 * password hashing and verification.
 *
 * <p>Centralising the encoder in a single service keeps the strength parameter
 * consistent across the application and makes it easy to swap algorithms in
 * the future.</p>
 */
@Service
public class PasswordService {

    private final BCryptPasswordEncoder encoder;

    public PasswordService() {
        this.encoder = new BCryptPasswordEncoder(12);
    }

    /**
     * Hashes a raw-text password using BCrypt.
     *
     * @param rawPassword the plain-text password
     * @return the BCrypt hash
     */
    public String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    /**
     * Verifies a raw-text password against a stored BCrypt hash.
     *
     * @param rawPassword the plain-text password to check
     * @param encodedHash the stored BCrypt hash
     * @return {@code true} if the password matches the hash
     */
    public boolean matches(String rawPassword, String encodedHash) {
        return encoder.matches(rawPassword, encodedHash);
    }

    /**
     * Exposes the underlying encoder for use as a Spring Security
     * {@link org.springframework.security.crypto.password.PasswordEncoder} bean.
     *
     * @return the BCrypt password encoder instance
     */
    public BCryptPasswordEncoder getEncoder() {
        return encoder;
    }
}
