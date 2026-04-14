package com.eventops.domain.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "security_settings")
public class SecuritySettings {

    public static final String DEFAULT_ID = "default";

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "rate_limit_per_minute", nullable = false)
    private int rateLimitPerMinute;

    @Column(name = "login_max_attempts", nullable = false)
    private int loginMaxAttempts;

    @Column(name = "login_lockout_minutes", nullable = false)
    private int loginLockoutMinutes;

    @Column(name = "signature_enabled", nullable = false)
    private boolean signatureEnabled;

    @Column(name = "signature_algorithm", nullable = false, length = 50)
    private String signatureAlgorithm;

    @Column(name = "signature_max_age_seconds", nullable = false)
    private int signatureMaxAgeSeconds;

    @Column(name = "pii_display_mode", nullable = false, length = 30)
    private String piiDisplayMode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null || id.isBlank()) {
            id = DEFAULT_ID;
        }
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getRateLimitPerMinute() {
        return rateLimitPerMinute;
    }

    public void setRateLimitPerMinute(int rateLimitPerMinute) {
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    public int getLoginMaxAttempts() {
        return loginMaxAttempts;
    }

    public void setLoginMaxAttempts(int loginMaxAttempts) {
        this.loginMaxAttempts = loginMaxAttempts;
    }

    public int getLoginLockoutMinutes() {
        return loginLockoutMinutes;
    }

    public void setLoginLockoutMinutes(int loginLockoutMinutes) {
        this.loginLockoutMinutes = loginLockoutMinutes;
    }

    public boolean isSignatureEnabled() {
        return signatureEnabled;
    }

    public void setSignatureEnabled(boolean signatureEnabled) {
        this.signatureEnabled = signatureEnabled;
    }

    public String getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    public void setSignatureAlgorithm(String signatureAlgorithm) {
        this.signatureAlgorithm = signatureAlgorithm;
    }

    public int getSignatureMaxAgeSeconds() {
        return signatureMaxAgeSeconds;
    }

    public void setSignatureMaxAgeSeconds(int signatureMaxAgeSeconds) {
        this.signatureMaxAgeSeconds = signatureMaxAgeSeconds;
    }

    public String getPiiDisplayMode() {
        return piiDisplayMode;
    }

    public void setPiiDisplayMode(String piiDisplayMode) {
        this.piiDisplayMode = piiDisplayMode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
