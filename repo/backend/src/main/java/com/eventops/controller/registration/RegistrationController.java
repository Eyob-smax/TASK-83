package com.eventops.controller.registration;

import com.eventops.common.dto.ApiResponse;
import com.eventops.common.dto.registration.RegistrationRequest;
import com.eventops.common.dto.registration.RegistrationResponse;
import com.eventops.security.auth.EventOpsUserDetails;
import com.eventops.service.registration.RegistrationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing event registrations: register, list, cancel,
 * and view waitlist positions.
 *
 * <p>All endpoints return the standard {@link ApiResponse} envelope.</p>
 */
@RestController
@RequestMapping("/api/registrations")
public class RegistrationController {

    private static final Logger log = LoggerFactory.getLogger(RegistrationController.class);

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    /**
     * Registers the current user for an event session. If the session is full,
     * the user is added to the waitlist.
     *
     * @param request   the registration request containing the session ID
     * @param principal the authenticated user
     * @return 201 with registration data and appropriate message
     */
    @PostMapping
    public ResponseEntity<ApiResponse<RegistrationResponse>> register(
            @Valid @RequestBody RegistrationRequest request,
            @AuthenticationPrincipal EventOpsUserDetails principal) {
        String userId = principal.getUser().getId();
        log.debug("POST /api/registrations – userId={}, sessionId={}", userId, request.getSessionId());

        RegistrationResponse response = registrationService.register(userId, request.getSessionId());

        String message;
        if ("WAITLISTED".equals(response.getStatus())) {
            message = "Added to waitlist at position " + response.getWaitlistPosition();
        } else {
            message = "Registration confirmed";
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, message));
    }

    /**
     * Lists all registrations for the current user.
     *
     * @param principal the authenticated user
     * @return 200 with list of registrations
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<RegistrationResponse>>> list(
            @AuthenticationPrincipal EventOpsUserDetails principal) {
        String userId = principal.getUser().getId();
        log.debug("GET /api/registrations – userId={}", userId);

        List<RegistrationResponse> registrations = registrationService.getUserRegistrations(userId);
        return ResponseEntity.ok(ApiResponse.success(registrations));
    }

    /**
     * Retrieves a single registration by ID for the current user.
     *
     * @param id        the registration identifier
     * @param principal the authenticated user
     * @return 200 with registration data
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RegistrationResponse>> get(
            @PathVariable String id,
            @AuthenticationPrincipal EventOpsUserDetails principal) {
        String userId = principal.getUser().getId();
        log.debug("GET /api/registrations/{} – userId={}", id, userId);

        RegistrationResponse registration = registrationService.getRegistration(id, userId);
        return ResponseEntity.ok(ApiResponse.success(registration));
    }

    /**
     * Cancels a registration for the current user.
     *
     * @param id        the registration identifier
     * @param principal the authenticated user
     * @return 200 with cancelled registration data
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<RegistrationResponse>> cancel(
            @PathVariable String id,
            @AuthenticationPrincipal EventOpsUserDetails principal) {
        String userId = principal.getUser().getId();
        log.debug("DELETE /api/registrations/{} – userId={}", id, userId);

        RegistrationResponse cancelled = registrationService.cancel(userId, id);
        return ResponseEntity.ok(ApiResponse.success(cancelled, "Registration cancelled"));
    }

    /**
     * Returns the current user's waitlist positions across all sessions.
     *
     * @param principal the authenticated user
     * @return 200 with list of waitlisted registrations
     */
    @GetMapping("/waitlist")
    public ResponseEntity<ApiResponse<List<RegistrationResponse>>> waitlist(
            @AuthenticationPrincipal EventOpsUserDetails principal) {
        String userId = principal.getUser().getId();
        log.debug("GET /api/registrations/waitlist – userId={}", userId);

        List<RegistrationResponse> waitlistPositions = registrationService.getWaitlistPositions(userId);
        return ResponseEntity.ok(ApiResponse.success(waitlistPositions));
    }

    /**
     * Returns all registrations for a given session. Accessible by EVENT_STAFF
     * and SYSTEM_ADMIN for roster management and check-in preparation.
     *
     * @param sessionId the session identifier
     * @return 200 with the session roster
     */
    @GetMapping("/session/{sessionId}")
    @PreAuthorize("hasAnyRole('EVENT_STAFF', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<List<RegistrationResponse>>> getSessionRoster(
            @PathVariable String sessionId) {
        log.debug("GET /api/registrations/session/{} – roster request", sessionId);

        List<RegistrationResponse> roster = registrationService.getSessionRoster(sessionId);
        return ResponseEntity.ok(ApiResponse.success(roster));
    }
}
