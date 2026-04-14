package com.eventops.service.admin;

import com.eventops.audit.logging.AuditService;
import com.eventops.audit.logging.FieldChange;
import com.eventops.common.dto.admin.SecuritySettingsRequest;
import com.eventops.common.dto.admin.SecuritySettingsResponse;
import com.eventops.config.AppConstants;
import com.eventops.domain.admin.SecuritySettings;
import com.eventops.domain.audit.AuditActionType;
import com.eventops.repository.admin.SecuritySettingsRepository;
import com.eventops.security.ratelimit.RateLimitProperties;
import com.eventops.security.signature.SignatureProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class SecuritySettingsService {

    private static final String DEFAULT_PII_DISPLAY_MODE = "MASKED";

    private final SecuritySettingsRepository securitySettingsRepository;
    private final AuditService auditService;
    private final RateLimitProperties rateLimitProperties;
    private final SignatureProperties signatureProperties;

    public SecuritySettingsService(SecuritySettingsRepository securitySettingsRepository,
                                   AuditService auditService,
                                   RateLimitProperties rateLimitProperties,
                                   SignatureProperties signatureProperties) {
        this.securitySettingsRepository = securitySettingsRepository;
        this.auditService = auditService;
        this.rateLimitProperties = rateLimitProperties;
        this.signatureProperties = signatureProperties;
    }

    public SecuritySettings getEffectiveSettings() {
        return securitySettingsRepository.findById(SecuritySettings.DEFAULT_ID)
                .orElseGet(this::createDefaultSettings);
    }

    public SecuritySettingsResponse getSettingsResponse() {
        return mapToResponse(getEffectiveSettings());
    }

    public SecuritySettingsResponse updateSettings(SecuritySettingsRequest request,
                                                   String operatorId,
                                                   String operatorName) {
        SecuritySettings settings = securitySettingsRepository.findById(SecuritySettings.DEFAULT_ID)
                .orElseGet(this::createDefaultSettings);

        List<FieldChange> changes = new ArrayList<>();
        recordChange(changes, "rateLimitPerMinute", settings.getRateLimitPerMinute(), request.getRateLimitPerMinute());
        recordChange(changes, "loginMaxAttempts", settings.getLoginMaxAttempts(), request.getLoginMaxAttempts());
        recordChange(changes, "loginLockoutMinutes", settings.getLoginLockoutMinutes(), request.getLoginLockoutMinutes());
        recordChange(changes, "signatureEnabled", settings.isSignatureEnabled(), request.getSignatureEnabled());
        recordChange(changes, "signatureAlgorithm", settings.getSignatureAlgorithm(), request.getSignatureAlgorithm());
        recordChange(changes, "signatureMaxAgeSeconds", settings.getSignatureMaxAgeSeconds(), request.getSignatureMaxAgeSeconds());
        recordChange(changes, "piiDisplayMode", settings.getPiiDisplayMode(), request.getPiiDisplayMode());

        settings.setRateLimitPerMinute(request.getRateLimitPerMinute());
        settings.setLoginMaxAttempts(request.getLoginMaxAttempts());
        settings.setLoginLockoutMinutes(request.getLoginLockoutMinutes());
        settings.setSignatureEnabled(Boolean.TRUE.equals(request.getSignatureEnabled()));
        settings.setSignatureAlgorithm(request.getSignatureAlgorithm());
        settings.setSignatureMaxAgeSeconds(request.getSignatureMaxAgeSeconds());
        settings.setPiiDisplayMode(request.getPiiDisplayMode());

        SecuritySettings saved = securitySettingsRepository.save(settings);
        if (!changes.isEmpty()) {
            auditService.logWithDiffs(
                    AuditActionType.SECURITY_SETTING_CHANGED,
                    operatorId,
                    operatorName,
                    "WEB",
                    "SecuritySettings",
                    saved.getId(),
                    "Security settings updated by administrator",
                    changes
            );
        }

        return mapToResponse(saved);
    }

    public int getRateLimitPerMinute() {
        return getEffectiveSettings().getRateLimitPerMinute();
    }

    public int getLoginMaxAttempts() {
        return getEffectiveSettings().getLoginMaxAttempts();
    }

    public int getLoginLockoutMinutes() {
        return getEffectiveSettings().getLoginLockoutMinutes();
    }

    public boolean isSignatureEnabled() {
        return getEffectiveSettings().isSignatureEnabled();
    }

    public String getSignatureAlgorithm() {
        return getEffectiveSettings().getSignatureAlgorithm();
    }

    public int getSignatureMaxAgeSeconds() {
        return getEffectiveSettings().getSignatureMaxAgeSeconds();
    }

    private SecuritySettings createDefaultSettings() {
        SecuritySettings settings = new SecuritySettings();
        settings.setId(SecuritySettings.DEFAULT_ID);
        settings.setRateLimitPerMinute(rateLimitProperties.getRequestsPerMinute());
        settings.setLoginMaxAttempts(AppConstants.LOGIN_MAX_ATTEMPTS);
        settings.setLoginLockoutMinutes(AppConstants.LOGIN_LOCKOUT_MINUTES);
        settings.setSignatureEnabled(signatureProperties.isEnabled());
        settings.setSignatureAlgorithm(signatureProperties.getAlgorithm());
        settings.setSignatureMaxAgeSeconds(signatureProperties.getMaxAgeSeconds());
        settings.setPiiDisplayMode(DEFAULT_PII_DISPLAY_MODE);
        return securitySettingsRepository.save(settings);
    }

    private SecuritySettingsResponse mapToResponse(SecuritySettings settings) {
        SecuritySettingsResponse response = new SecuritySettingsResponse();
        response.setId(settings.getId());
        response.setRateLimitPerMinute(settings.getRateLimitPerMinute());
        response.setLoginMaxAttempts(settings.getLoginMaxAttempts());
        response.setLoginLockoutMinutes(settings.getLoginLockoutMinutes());
        response.setSignatureEnabled(settings.isSignatureEnabled());
        response.setSignatureAlgorithm(settings.getSignatureAlgorithm());
        response.setSignatureMaxAgeSeconds(settings.getSignatureMaxAgeSeconds());
        response.setPiiDisplayMode(settings.getPiiDisplayMode());
        response.setUpdatedAt(settings.getUpdatedAt());
        return response;
    }

    private void recordChange(List<FieldChange> changes, String fieldName, Object previousValue, Object nextValue) {
        String previous = previousValue == null ? null : String.valueOf(previousValue);
        String next = nextValue == null ? null : String.valueOf(nextValue);
        if ((previous == null && next != null) || (previous != null && !previous.equals(next))) {
            changes.add(new FieldChange(fieldName, previous, next));
        }
    }
}
