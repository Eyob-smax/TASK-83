package com.eventops.service;

import com.eventops.audit.logging.AuditService;
import com.eventops.common.dto.admin.SecuritySettingsRequest;
import com.eventops.common.dto.admin.SecuritySettingsResponse;
import com.eventops.domain.admin.SecuritySettings;
import com.eventops.repository.admin.SecuritySettingsRepository;
import com.eventops.security.ratelimit.RateLimitProperties;
import com.eventops.security.signature.SignatureProperties;
import com.eventops.service.admin.SecuritySettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecuritySettingsServiceTest {

    @Mock SecuritySettingsRepository repo;
    @Mock AuditService auditService;
    @Mock RateLimitProperties rateLimitProperties;
    @Mock SignatureProperties signatureProperties;
    @InjectMocks SecuritySettingsService service;

    private SecuritySettings existingSettings() {
        SecuritySettings s = new SecuritySettings();
        s.setId(SecuritySettings.DEFAULT_ID);
        s.setRateLimitPerMinute(60);
        s.setLoginMaxAttempts(5);
        s.setLoginLockoutMinutes(15);
        s.setSignatureEnabled(false);
        s.setSignatureAlgorithm("HmacSHA256");
        s.setSignatureMaxAgeSeconds(300);
        s.setPiiDisplayMode("MASKED");
        return s;
    }

    @Test
    void getEffectiveSettings_returnsExistingWhenPresent() {
        SecuritySettings existing = existingSettings();
        when(repo.findById(SecuritySettings.DEFAULT_ID)).thenReturn(Optional.of(existing));

        SecuritySettings result = service.getEffectiveSettings();

        assertSame(existing, result);
    }

    @Test
    void getEffectiveSettings_createsDefaultWhenMissing() {
        when(repo.findById(SecuritySettings.DEFAULT_ID)).thenReturn(Optional.empty());
        when(rateLimitProperties.getRequestsPerMinute()).thenReturn(60);
        when(signatureProperties.isEnabled()).thenReturn(false);
        when(signatureProperties.getAlgorithm()).thenReturn("HmacSHA256");
        when(signatureProperties.getMaxAgeSeconds()).thenReturn(300);
        when(repo.save(any(SecuritySettings.class))).thenAnswer(inv -> inv.getArgument(0));

        SecuritySettings result = service.getEffectiveSettings();

        assertNotNull(result);
        assertEquals("HmacSHA256", result.getSignatureAlgorithm());
        verify(repo).save(any(SecuritySettings.class));
    }

    @Test
    void getSettingsResponse_returnsMappedResponse() {
        when(repo.findById(SecuritySettings.DEFAULT_ID)).thenReturn(Optional.of(existingSettings()));

        SecuritySettingsResponse resp = service.getSettingsResponse();

        assertEquals(60, resp.getRateLimitPerMinute());
        assertEquals("HmacSHA256", resp.getSignatureAlgorithm());
    }

    @Test
    void updateSettings_withChanges_triggersAuditLog() {
        SecuritySettings existing = existingSettings();
        when(repo.findById(SecuritySettings.DEFAULT_ID)).thenReturn(Optional.of(existing));
        when(repo.save(any(SecuritySettings.class))).thenAnswer(inv -> inv.getArgument(0));

        SecuritySettingsRequest req = new SecuritySettingsRequest();
        req.setRateLimitPerMinute(120);
        req.setLoginMaxAttempts(3);
        req.setLoginLockoutMinutes(30);
        req.setSignatureEnabled(true);
        req.setSignatureAlgorithm("HmacSHA512");
        req.setSignatureMaxAgeSeconds(600);
        req.setPiiDisplayMode("REVEALED");

        service.updateSettings(req, "admin", "Admin One");

        verify(auditService).logWithDiffs(any(), eq("admin"), eq("Admin One"), eq("WEB"),
                eq("SecuritySettings"), anyString(), anyString(), anyList());
    }

    @Test
    void updateSettings_withNoChanges_skipsAuditLog() {
        SecuritySettings existing = existingSettings();
        when(repo.findById(SecuritySettings.DEFAULT_ID)).thenReturn(Optional.of(existing));
        when(repo.save(any(SecuritySettings.class))).thenAnswer(inv -> inv.getArgument(0));

        SecuritySettingsRequest req = new SecuritySettingsRequest();
        req.setRateLimitPerMinute(60);
        req.setLoginMaxAttempts(5);
        req.setLoginLockoutMinutes(15);
        req.setSignatureEnabled(false);
        req.setSignatureAlgorithm("HmacSHA256");
        req.setSignatureMaxAgeSeconds(300);
        req.setPiiDisplayMode("MASKED");

        service.updateSettings(req, "admin", "Admin");

        verify(auditService, never()).logWithDiffs(any(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyList());
    }

    @Test
    void getRateLimitPerMinute_delegatesToSettings() {
        when(repo.findById(SecuritySettings.DEFAULT_ID)).thenReturn(Optional.of(existingSettings()));
        assertEquals(60, service.getRateLimitPerMinute());
    }

    @Test
    void getLoginMaxAttempts_delegatesToSettings() {
        when(repo.findById(SecuritySettings.DEFAULT_ID)).thenReturn(Optional.of(existingSettings()));
        assertEquals(5, service.getLoginMaxAttempts());
    }

    @Test
    void getLoginLockoutMinutes_delegatesToSettings() {
        when(repo.findById(SecuritySettings.DEFAULT_ID)).thenReturn(Optional.of(existingSettings()));
        assertEquals(15, service.getLoginLockoutMinutes());
    }

    @Test
    void isSignatureEnabled_delegatesToSettings() {
        when(repo.findById(SecuritySettings.DEFAULT_ID)).thenReturn(Optional.of(existingSettings()));
        assertFalse(service.isSignatureEnabled());
    }

    @Test
    void getSignatureAlgorithm_delegatesToSettings() {
        when(repo.findById(SecuritySettings.DEFAULT_ID)).thenReturn(Optional.of(existingSettings()));
        assertEquals("HmacSHA256", service.getSignatureAlgorithm());
    }

    @Test
    void getSignatureMaxAgeSeconds_delegatesToSettings() {
        when(repo.findById(SecuritySettings.DEFAULT_ID)).thenReturn(Optional.of(existingSettings()));
        assertEquals(300, service.getSignatureMaxAgeSeconds());
    }

    @Test
    void updateSettings_nullSignatureEnabled_treatedAsFalse() {
        SecuritySettings existing = existingSettings();
        existing.setSignatureEnabled(true);
        when(repo.findById(SecuritySettings.DEFAULT_ID)).thenReturn(Optional.of(existing));
        when(repo.save(any(SecuritySettings.class))).thenAnswer(inv -> inv.getArgument(0));

        SecuritySettingsRequest req = new SecuritySettingsRequest();
        req.setRateLimitPerMinute(60);
        req.setLoginMaxAttempts(5);
        req.setLoginLockoutMinutes(15);
        req.setSignatureEnabled(null);
        req.setSignatureAlgorithm("HmacSHA256");
        req.setSignatureMaxAgeSeconds(300);
        req.setPiiDisplayMode("MASKED");

        service.updateSettings(req, "admin", "Admin");

        assertFalse(existing.isSignatureEnabled());
    }
}
