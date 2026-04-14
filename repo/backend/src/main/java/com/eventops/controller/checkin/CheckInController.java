package com.eventops.controller.checkin;

import com.eventops.common.dto.ApiResponse;
import com.eventops.common.dto.checkin.CheckInRequest;
import com.eventops.common.dto.checkin.CheckInResponse;
import com.eventops.common.dto.checkin.PasscodeResponse;
import com.eventops.security.auth.EventOpsUserDetails;
import com.eventops.service.checkin.CheckInService;
import com.eventops.service.checkin.PasscodeService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for check-in operations: performing check-ins, retrieving
 * session passcodes, viewing rosters, and reviewing device conflicts.
 *
 * <p>All endpoints return the standard {@link ApiResponse} envelope.</p>
 */
@RestController
@RequestMapping("/api/checkin")
public class CheckInController {

    private static final Logger log = LoggerFactory.getLogger(CheckInController.class);

    private final CheckInService checkInService;
    private final PasscodeService passcodeService;

    public CheckInController(CheckInService checkInService, PasscodeService passcodeService) {
        this.checkInService = checkInService;
        this.passcodeService = passcodeService;
    }

    /**
     * Performs a check-in for an attendee at a session.
     *
     * @param sessionId the session identifier
     * @param request   the check-in request containing userId, passcode, and optional deviceToken
     * @param principal the authenticated staff member performing the check-in
     * @return 200 with check-in result
     */
    @PostMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<CheckInResponse>> checkIn(
            @PathVariable String sessionId,
            @Valid @RequestBody CheckInRequest request,
            @AuthenticationPrincipal EventOpsUserDetails principal) {
        String staffId = principal.getUser().getId();
        log.debug("POST /api/checkin/sessions/{} – staffId={}, attendeeId={}",
                sessionId, staffId, request.getUserId());

        CheckInResponse response = checkInService.checkIn(
                sessionId,
                request.getUserId(),
                staffId,
                request.getPasscode(),
                request.getDeviceToken()
        );

        return ResponseEntity.ok(ApiResponse.success(response, "Check-in successful"));
    }

    /**
     * Returns the current rotating passcode for a session.
     *
     * @param sessionId the session identifier
     * @return 200 with passcode data including remaining seconds
     */
    @GetMapping("/sessions/{sessionId}/passcode")
    public ResponseEntity<ApiResponse<PasscodeResponse>> getPasscode(@PathVariable String sessionId) {
        log.debug("GET /api/checkin/sessions/{}/passcode", sessionId);

        PasscodeResponse passcode = passcodeService.getCurrentPasscode(sessionId);
        return ResponseEntity.ok(ApiResponse.success(passcode));
    }

    /**
     * Returns the check-in roster for a session.
     *
     * @param sessionId the session identifier
     * @return 200 with list of check-in records
     */
    @GetMapping("/sessions/{sessionId}/roster")
    public ResponseEntity<ApiResponse<List<CheckInResponse>>> getRoster(@PathVariable String sessionId) {
        log.debug("GET /api/checkin/sessions/{}/roster", sessionId);

        List<CheckInResponse> roster = checkInService.getRoster(sessionId);
        return ResponseEntity.ok(ApiResponse.success(roster));
    }

    /**
     * Returns device conflict records for a session.
     *
     * @param sessionId the session identifier
     * @return 200 with list of conflict check-in records
     */
    @GetMapping("/sessions/{sessionId}/conflicts")
    public ResponseEntity<ApiResponse<List<CheckInResponse>>> getConflicts(@PathVariable String sessionId) {
        log.debug("GET /api/checkin/sessions/{}/conflicts", sessionId);

        List<CheckInResponse> conflicts = checkInService.getConflicts(sessionId);
        return ResponseEntity.ok(ApiResponse.success(conflicts));
    }
}
