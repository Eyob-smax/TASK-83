package com.eventops.security;

import com.eventops.domain.user.AccountStatus;
import com.eventops.domain.user.RoleType;
import com.eventops.domain.user.User;
import com.eventops.repository.user.UserRepository;
import com.eventops.security.auth.LoginThrottleService;
import com.eventops.service.admin.SecuritySettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginThrottleServiceTest {

    @Mock UserRepository userRepository;
    @Mock SecuritySettingsService securitySettingsService;
    @InjectMocks LoginThrottleService service;

    private User baseUser() {
        User u = new User();
        u.setId("u1");
        u.setUsername("alice");
        u.setPasswordHash("h");
        u.setDisplayName("Alice");
        u.setRoleType(RoleType.ATTENDEE);
        u.setStatus(AccountStatus.ACTIVE);
        u.setFailedLoginAttempts(0);
        return u;
    }

    @Test
    void recordFailedAttempt_incrementsCounterWithoutLocking() {
        when(securitySettingsService.getLoginMaxAttempts()).thenReturn(5);
        when(securitySettingsService.getLoginLockoutMinutes()).thenReturn(15);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        User u = baseUser();

        service.recordFailedAttempt(u);

        assertEquals(1, u.getFailedLoginAttempts());
        assertNull(u.getLockoutUntil());
        assertEquals(AccountStatus.ACTIVE, u.getStatus());
    }

    @Test
    void recordFailedAttempt_locksAccountAtMaxAttempts() {
        when(securitySettingsService.getLoginMaxAttempts()).thenReturn(5);
        when(securitySettingsService.getLoginLockoutMinutes()).thenReturn(15);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        User u = baseUser();
        u.setFailedLoginAttempts(4);

        service.recordFailedAttempt(u);

        assertEquals(5, u.getFailedLoginAttempts());
        assertNotNull(u.getLockoutUntil());
        assertEquals(AccountStatus.LOCKED, u.getStatus());
    }

    @Test
    void recordSuccessfulLogin_resetsAllCountersAndSetsLastLogin() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        User u = baseUser();
        u.setFailedLoginAttempts(3);
        u.setLockoutUntil(Instant.now().plusSeconds(60));

        service.recordSuccessfulLogin(u);

        assertEquals(0, u.getFailedLoginAttempts());
        assertNull(u.getLockoutUntil());
        assertNotNull(u.getLastLoginAt());
    }

    @Test
    void isLockedOut_returnsFalseForNonLockedStatus() {
        User u = baseUser();
        assertFalse(service.isLockedOut(u));
    }

    @Test
    void isLockedOut_returnsTrueForLockedWithFutureLockoutUntil() {
        User u = baseUser();
        u.setStatus(AccountStatus.LOCKED);
        u.setLockoutUntil(Instant.now().plusSeconds(300));

        assertTrue(service.isLockedOut(u));
    }

    @Test
    void isLockedOut_returnsTrueForLockedWithNoLockoutUntil_manualLock() {
        User u = baseUser();
        u.setStatus(AccountStatus.LOCKED);
        u.setLockoutUntil(null);

        assertTrue(service.isLockedOut(u));
    }

    @Test
    void isLockedOut_autoUnlocksWhenLockoutExpired() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        User u = baseUser();
        u.setStatus(AccountStatus.LOCKED);
        u.setLockoutUntil(Instant.now().minusSeconds(60));
        u.setFailedLoginAttempts(5);

        assertFalse(service.isLockedOut(u));
        assertEquals(AccountStatus.ACTIVE, u.getStatus());
        assertEquals(0, u.getFailedLoginAttempts());
        assertNull(u.getLockoutUntil());
    }
}
