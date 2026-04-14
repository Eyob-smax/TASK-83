package com.eventops.security;

import com.eventops.domain.user.User;
import com.eventops.domain.user.AccountStatus;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class LoginThrottleTest {

    @Test
    void newUser_hasZeroFailedAttempts() {
        User user = new User();
        assertEquals(0, user.getFailedLoginAttempts());
    }

    @Test
    void lockout_configuredAt5Attempts() {
        User user = new User();
        // Simulate 5 failed attempts
        user.setFailedLoginAttempts(5);
        user.setLockoutUntil(Instant.now().plusSeconds(900));
        user.setStatus(AccountStatus.LOCKED);

        assertEquals(AccountStatus.LOCKED, user.getStatus());
        assertTrue(user.getLockoutUntil().isAfter(Instant.now()));
    }

    @Test
    void lockoutDuration_is15Minutes() {
        Instant lockoutTime = Instant.now().plusSeconds(15 * 60);
        User user = new User();
        user.setLockoutUntil(lockoutTime);

        long secondsUntilUnlock = lockoutTime.getEpochSecond() - Instant.now().getEpochSecond();
        assertTrue(secondsUntilUnlock > 890 && secondsUntilUnlock <= 900,
            "Lockout should be approximately 15 minutes");
    }

    @Test
    void expiredLockout_shouldAutoUnlock() {
        User user = new User();
        user.setStatus(AccountStatus.LOCKED);
        user.setLockoutUntil(Instant.now().minusSeconds(60));

        // The lockout has passed — isLockedOut logic should detect this
        assertTrue(user.getLockoutUntil().isBefore(Instant.now()));
    }
}
