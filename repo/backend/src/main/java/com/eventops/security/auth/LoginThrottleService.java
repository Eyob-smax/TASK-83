package com.eventops.security.auth;

import com.eventops.domain.user.AccountStatus;
import com.eventops.domain.user.User;
import com.eventops.repository.user.UserRepository;
import com.eventops.service.admin.SecuritySettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Tracks failed login attempts and enforces account lockout policy.
 *
 * <p>Policy:
 * <ul>
 *   <li>After 5 consecutive failed attempts the account is locked for 15 minutes.</li>
 *   <li>A successful login resets the failure counter and lockout window.</li>
 *   <li>When checked, an expired lockout is automatically cleared (auto-unlock).</li>
 * </ul>
 * </p>
 */
@Service
public class LoginThrottleService {

    private static final Logger log = LoggerFactory.getLogger(LoginThrottleService.class);

    private final UserRepository userRepository;
    private final SecuritySettingsService securitySettingsService;

    public LoginThrottleService(UserRepository userRepository,
                                SecuritySettingsService securitySettingsService) {
        this.userRepository = userRepository;
        this.securitySettingsService = securitySettingsService;
    }

    /**
     * Records a failed login attempt. If the threshold is reached the account
     * is locked for {@value #MAX_FAILED_ATTEMPTS} minutes.
     *
     * @param user the user who failed authentication
     */
    @Transactional
    public void recordFailedAttempt(User user) {
        int maxFailedAttempts = securitySettingsService.getLoginMaxAttempts();
        Duration lockoutDuration = Duration.ofMinutes(securitySettingsService.getLoginLockoutMinutes());
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= maxFailedAttempts) {
            user.setLockoutUntil(Instant.now().plus(lockoutDuration));
            user.setStatus(AccountStatus.LOCKED);
            log.warn("Account locked for user '{}' after {} failed attempts",
                    user.getUsername(), attempts);
        }

        userRepository.save(user);
    }

    /**
     * Records a successful login: resets counters and updates the last-login
     * timestamp.
     *
     * @param user the user who authenticated successfully
     */
    @Transactional
    public void recordSuccessfulLogin(User user) {
        user.setFailedLoginAttempts(0);
        user.setLockoutUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
    }

    /**
     * Checks whether the account is currently locked out.
     *
     * <p>If the lockout window has expired the account is automatically
     * unlocked (status reset to {@code ACTIVE}, counters cleared).</p>
     *
     * @param user the user to check
     * @return {@code true} if the account is still locked
     */
    @Transactional
    public boolean isLockedOut(User user) {
        if (user.getStatus() != AccountStatus.LOCKED) {
            return false;
        }

        if (user.getLockoutUntil() == null) {
            // Locked without a timed window (e.g. manually locked) -- stays locked.
            return true;
        }

        if (user.getLockoutUntil().isAfter(Instant.now())) {
            return true;
        }

        // Lockout has expired -- auto-unlock.
        user.setStatus(AccountStatus.ACTIVE);
        user.setFailedLoginAttempts(0);
        user.setLockoutUntil(null);
        userRepository.save(user);
        log.info("Auto-unlocked account for user '{}'", user.getUsername());
        return false;
    }
}
