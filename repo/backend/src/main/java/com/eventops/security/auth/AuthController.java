package com.eventops.security.auth;

import com.eventops.common.dto.ApiResponse;
import com.eventops.common.dto.auth.LoginRequest;
import com.eventops.common.dto.auth.RegisterAccountRequest;
import com.eventops.common.dto.auth.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for authentication operations: login, registration,
 * current-user retrieval, and logout.
 *
 * <p>All endpoints return the standard {@link ApiResponse} envelope.</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    public static final String SESSION_SIGNATURE_TOKEN_KEY = "EVENTOPS_SIGNATURE_TOKEN";
    public static final String SESSION_SIGNATURE_TOKEN_HEADER = "X-Session-Signature-Token";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Authenticates a user with username and password.
     *
     * @param request the login credentials
     * @return 200 with user data on success, 401 on bad credentials,
     *         429 if the account is locked
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserResponse>> login(@Valid @RequestBody LoginRequest request,
                                                           HttpServletRequest httpRequest) {
        try {
            var authenticatedUser = authService.authenticate(request);
            EventOpsUserDetails principal = new EventOpsUserDetails(authenticatedUser);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            SecurityContextImpl securityContext = new SecurityContextImpl(authentication);
            SecurityContextHolder.setContext(securityContext);

            HttpSession session = httpRequest.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
                String signatureToken = issueSessionSignatureToken(session);

            UserResponse userResponse = authService.mapToResponse(authenticatedUser);
                return ResponseEntity.ok()
                    .header(SESSION_SIGNATURE_TOKEN_HEADER, signatureToken)
                    .body(ApiResponse.success(userResponse, "Login successful"));
        } catch (AuthService.AccountLockedException ex) {
            log.warn("Login attempt on locked account: {}", request.getUsername());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error(ex.getMessage(), List.of(
                            new ApiResponse.ApiError("account", "ACCOUNT_LOCKED", ex.getMessage()))));
        } catch (BadCredentialsException | UsernameNotFoundException ex) {
            log.debug("Failed login attempt for username: {}", request.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid username or password", List.of(
                            new ApiResponse.ApiError("credentials", "BAD_CREDENTIALS",
                                    "Invalid username or password"))));
        }
    }

    /**
     * Registers a new attendee account.
     *
     * @param request the registration details
     * @return 201 with user data on success, 409 if the username is taken
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody RegisterAccountRequest request) {
        try {
            UserResponse userResponse = authService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(userResponse, "Registration successful"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(ex.getMessage(), List.of(
                            new ApiResponse.ApiError("username", "USERNAME_TAKEN", ex.getMessage()))));
        }
    }

    /**
     * Returns the profile of the currently authenticated user.
     *
     * @param principal the authenticated user's {@link EventOpsUserDetails}
     * @return 200 with user data, or 401 if not authenticated
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(
            @AuthenticationPrincipal EventOpsUserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Not authenticated", List.of(
                            new ApiResponse.ApiError("auth", "NOT_AUTHENTICATED",
                                    "No active session"))));
        }
        UserResponse userResponse = authService.getCurrentUser(principal.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success(userResponse));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<UserResponse>> refresh(
            @AuthenticationPrincipal EventOpsUserDetails principal,
            HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (principal == null || session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Not authenticated", List.of(
                            new ApiResponse.ApiError("auth", "NOT_AUTHENTICATED",
                                    "No active session"))));
        }

        String signatureToken = issueSessionSignatureToken(session);
        UserResponse userResponse = authService.getCurrentUser(principal.getUser().getId());
        return ResponseEntity.ok()
            .header(SESSION_SIGNATURE_TOKEN_HEADER, signatureToken)
            .body(ApiResponse.success(userResponse, "Session refreshed"));
    }

    /**
     * Invalidates the current HTTP session, effectively logging the user out.
     *
     * @param request the HTTP request (used to access the session)
     * @return 200 confirming logout
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(ApiResponse.success(null, "Logout successful"));
    }

    private String issueSessionSignatureToken(HttpSession session) {
        String token = UUID.randomUUID().toString();
        session.setAttribute(SESSION_SIGNATURE_TOKEN_KEY, token);
        return token;
    }
}
