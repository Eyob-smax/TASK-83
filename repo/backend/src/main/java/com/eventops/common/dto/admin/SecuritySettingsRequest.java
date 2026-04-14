package com.eventops.common.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class SecuritySettingsRequest {

    @Min(1)
    private int rateLimitPerMinute;

    @Min(1)
    private int loginMaxAttempts;

    @Min(1)
    private int loginLockoutMinutes;

    @NotNull
    private Boolean signatureEnabled;

    @NotBlank
    @Size(max = 50)
    private String signatureAlgorithm;

    @Min(1)
    private int signatureMaxAgeSeconds;

    @NotBlank
    @Size(max = 30)
    private String piiDisplayMode;

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

    public Boolean getSignatureEnabled() {
        return signatureEnabled;
    }

    public void setSignatureEnabled(Boolean signatureEnabled) {
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
}
