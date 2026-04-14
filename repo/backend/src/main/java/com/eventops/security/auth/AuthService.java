package com.eventops.security.auth;

import com.eventops.common.dto.auth.LoginRequest;
import com.eventops.common.dto.auth.RegisterAccountRequest;
import com.eventops.common.dto.auth.UserResponse;
import com.eventops.domain.user.AccountStatus;
import com.eventops.domain.user.RoleType;
import com.eventops.domain.user.User;
import com.eventops.repository.user.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core authentication and registration logic.
 *
 * <p>This service is the single point of truth for login, registration, and
 * user-response mapping. The {@link AuthController} delegates to this service
 * to remain thin.</p>
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final LoginThrottleService loginThrottleService;

    public AuthService(UserRepository userRepository,
                       PasswordService passwordService,
                       LoginThrottleService loginThrottleService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.loginThrottleService = loginThrottleService;
    }

    /**
     * Authenticates a user by username and password.
     *
     * @param request the login request containing credentials
     * @return a {@link UserResponse} on success
     * @throws AccountLockedException      if the account is currently locked out
     * @throws BadCredentialsException     if the password is incorrect
     * @throws UsernameNotFoundException   if the username does not exist
     */
    @Transactional
    public User authenticate(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Invalid username or password"));

        // Check lockout before verifying password.
        if (loginThrottleService.isLockedOut(user)) {
            throw new AccountLockedException("Account is locked. Please try again later.");
        }

        if (user.getStatus() == AccountStatus.DISABLED) {
            throw new BadCredentialsException("Account is disabled");
        }

        if (!passwordService.matches(request.getPassword(), user.getPasswordHash())) {
            loginThrottleService.recordFailedAttempt(user);
            throw new BadCredentialsException("Invalid username or password");
        }

        loginThrottleService.recordSuccessfulLogin(user);
        return user;
    }

    @Transactional
    public UserResponse login(LoginRequest request) {
        User user = authenticate(request);
        return mapToResponse(user);
    }

    /**
     * Registers a new user account with the {@code ATTENDEE} role.
     *
     * @param request the registration request
     * @return a {@link UserResponse} for the newly created user
     * @throws IllegalArgumentException if the username is already taken
     */
    @Transactional
    public UserResponse register(RegisterAccountRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username is already taken");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordService.encode(request.getPassword()));
        user.setDisplayName(request.getDisplayName());
        user.setRoleType(RoleType.ATTENDEE);
        user.setStatus(AccountStatus.ACTIVE);

        user.setContactInfo(request.getContactInfo());

        User saved = userRepository.save(user);
        return mapToResponse(saved);
    }

    /**
     * Retrieves the current user's information by user ID.
     *
     * @param userId the user's unique identifier
     * @return a {@link UserResponse} for the user
     * @throws UsernameNotFoundException if no user exists with the given ID
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return mapToResponse(user);
    }

    /**
     * Maps a {@link User} domain entity to a {@link UserResponse} DTO.
     * Contact information is always masked in the response.
     *
     * @param user the domain entity
     * @return the response DTO with masked contact info
     */
    public UserResponse mapToResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setDisplayName(user.getDisplayName());
        response.setContactInfoMasked("****");
        response.setRoleType(user.getRoleType().name());
        response.setStatus(user.getStatus().name());
        response.setLastLoginAt(user.getLastLoginAt());
        response.setCreatedAt(user.getCreatedAt());
        return response;
    }

    /**
     * Custom exception indicating the account is locked due to too many
     * failed login attempts.
     */
    public static class AccountLockedException extends RuntimeException {
        public AccountLockedException(String message) {
            super(message);
        }
    }
}
