package com.eventops.security.signature;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for request signature verification.
 *
 * <p>Binds to the {@code eventops.security.signature} prefix in
 * {@code application.yml}. The secret key should be provided via an
 * environment variable ({@code EVENTOPS_SECURITY_SIGNATURE_SECRET_KEY})
 * rather than stored in configuration files.</p>
 */
@Configuration
@ConfigurationProperties(prefix = "eventops.security.signature")
public class SignatureProperties {

    /**
     * Whether signature verification is enabled. When false, the
     * {@link SignatureVerificationFilter} is not registered.
     */
    private boolean enabled;

    /**
     * The HMAC algorithm to use (e.g. {@code HmacSHA256}).
     */
    private String algorithm = "HmacSHA256";

    /**
     * Maximum age in seconds for a request timestamp before it is
     * considered stale. Provides replay-attack protection.
     */
    private int maxAgeSeconds = 300;

    /**
     * The shared secret key used to compute HMAC signatures.
     * Must be supplied via environment variable in production.
     */
    private String secretKey;

    // --- Getters and setters ---

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public int getMaxAgeSeconds() {
        return maxAgeSeconds;
    }

    public void setMaxAgeSeconds(int maxAgeSeconds) {
        this.maxAgeSeconds = maxAgeSeconds;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }
}
