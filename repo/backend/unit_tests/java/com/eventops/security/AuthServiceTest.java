package com.eventops.security;

import com.eventops.common.dto.auth.LoginRequest;
import com.eventops.common.dto.auth.RegisterAccountRequest;
import com.eventops.common.dto.auth.UserResponse;
import com.eventops.domain.user.AccountStatus;
import com.eventops.domain.user.RoleType;
import com.eventops.domain.user.User;
import com.eventops.repository.user.UserRepository;
import com.eventops.security.auth.AuthService;
import com.eventops.security.auth.AuthService.AccountLockedException;
import com.eventops.security.auth.LoginThrottleService;
import com.eventops.security.auth.PasswordService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthService}: login, registration, and current-user lookup.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordService passwordService;

    @Mock
    private LoginThrottleService loginThrottleService;

    @InjectMocks
    private AuthService authService;

    // ------------------------------------------------------------------
    // login()
    // ------------------------------------------------------------------

    @Test
    void login_success_returnsUserResponse() {
        User user = buildUser("user-1", "alice", RoleType.ATTENDEE, AccountStatus.ACTIVE);
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("correct-password");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(loginThrottleService.isLockedOut(user)).thenReturn(false);
        when(passwordService.matches("correct-password", user.getPasswordHash())).thenReturn(true);

        UserResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("user-1", response.getId());
        assertEquals("alice", response.getUsername());
        assertEquals("ATTENDEE", response.getRoleType());
        assertEquals("ACTIVE", response.getStatus());

        verify(loginThrottleService).recordSuccessfulLogin(user);
        verify(loginThrottleService, never()).recordFailedAttempt(any());
    }

    @Test
    void login_failure_wrongPassword() {
        User user = buildUser("user-2", "bob", RoleType.ATTENDEE, AccountStatus.ACTIVE);
        LoginRequest request = new LoginRequest();
        request.setUsername("bob");
        request.setPassword("wrong-password");

        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
        when(loginThrottleService.isLockedOut(user)).thenReturn(false);
        when(passwordService.matches("wrong-password", user.getPasswordHash())).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.login(request));

        verify(loginThrottleService).recordFailedAttempt(user);
        verify(loginThrottleService, never()).recordSuccessfulLogin(any());
    }

    @Test
    void login_failure_lockedOut() {
        User user = buildUser("user-3", "charlie", RoleType.ATTENDEE, AccountStatus.LOCKED);
        LoginRequest request = new LoginRequest();
        request.setUsername("charlie");
        request.setPassword("any-password");

        when(userRepository.findByUsername("charlie")).thenReturn(Optional.of(user));
        when(loginThrottleService.isLockedOut(user)).thenReturn(true);

        AccountLockedException ex = assertThrows(AccountLockedException.class,
                () -> authService.login(request));

        assertTrue(ex.getMessage().contains("locked"));
        verify(passwordService, never()).matches(any(), any());
        verify(loginThrottleService, never()).recordFailedAttempt(any());
    }

    @Test
    void login_failure_usernameNotFound() {
        LoginRequest request = new LoginRequest();
        request.setUsername("nonexistent");
        request.setPassword("any-password");

        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> authService.login(request));
    }

    // ------------------------------------------------------------------
    // register()
    // ------------------------------------------------------------------

    @Test
    void register_success_createsAttendee() {
        RegisterAccountRequest request = new RegisterAccountRequest();
        request.setUsername("newuser");
        request.setPassword("securePass123");
        request.setDisplayName("New User");
        request.setContactInfo("contact@example.com");

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(passwordService.encode("securePass123")).thenReturn("$2a$12$hashedValue");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId("generated-id");
            return u;
        });

        UserResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("newuser", response.getUsername());
        assertEquals("New User", response.getDisplayName());
        assertEquals("ATTENDEE", response.getRoleType());
        assertEquals("ACTIVE", response.getStatus());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals("newuser", savedUser.getUsername());
        assertEquals("$2a$12$hashedValue", savedUser.getPasswordHash());
        assertEquals(RoleType.ATTENDEE, savedUser.getRoleType());
        assertEquals(AccountStatus.ACTIVE, savedUser.getStatus());
        assertNotNull(savedUser.getContactInfo());
    }

    @Test
    void register_failure_duplicateUsername() {
        RegisterAccountRequest request = new RegisterAccountRequest();
        request.setUsername("taken");
        request.setPassword("securePass123");
        request.setDisplayName("Duplicate");

        when(userRepository.existsByUsername("taken")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.register(request));

        assertTrue(ex.getMessage().contains("already taken"));
        verify(userRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // getCurrentUser()
    // ------------------------------------------------------------------

    @Test
    void getCurrentUser_success() {
        User user = buildUser("user-10", "diana", RoleType.EVENT_STAFF, AccountStatus.ACTIVE);

        when(userRepository.findById("user-10")).thenReturn(Optional.of(user));

        UserResponse response = authService.getCurrentUser("user-10");

        assertNotNull(response);
        assertEquals("user-10", response.getId());
        assertEquals("diana", response.getUsername());
        assertEquals("EVENT_STAFF", response.getRoleType());
    }

    @Test
    void getCurrentUser_notFound() {
        when(userRepository.findById("missing-id")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> authService.getCurrentUser("missing-id"));
    }

    // ------------------------------------------------------------------
    // mapToResponse()
    // ------------------------------------------------------------------

    @Test
    void mapToResponse_masksContactInfo() {
        User user = buildUser("user-20", "eve", RoleType.SYSTEM_ADMIN, AccountStatus.ACTIVE);
        user.setContactInfo("sensitive@data.com");

        UserResponse response = authService.mapToResponse(user);

        assertEquals("****", response.getContactInfoMasked());
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    private User buildUser(String id, String username, RoleType role, AccountStatus status) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName(username + " Display");
        user.setPasswordHash("$2a$12$existingHash");
        user.setRoleType(role);
        user.setStatus(status);
        user.setLastLoginAt(Instant.now());
        return user;
    }
}
