package com.eventops.common.dto.admin;

import java.time.Instant;

public class SecuritySettingsResponse {

    private String id;
    private int rateLimitPerMinute;
    private int loginMaxAttempts;
    private int loginLockoutMinutes;
    private boolean signatureEnabled;
    private String signatureAlgorithm;
    private int signatureMaxAgeSeconds;
    private String piiDisplayMode;
    private Instant updatedAt;

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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
