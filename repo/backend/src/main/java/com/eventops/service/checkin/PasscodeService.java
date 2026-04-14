package com.eventops.service.checkin;

import com.eventops.common.dto.checkin.PasscodeResponse;
import com.eventops.common.exception.BusinessException;
import com.eventops.domain.checkin.PasscodeState;
import com.eventops.domain.event.EventSession;
import com.eventops.repository.checkin.PasscodeStateRepository;
import com.eventops.repository.event.EventSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Manages rotating passcodes used for attendee check-in verification.
 */
@Service
@Transactional
public class PasscodeService {

    private static final Logger log = LoggerFactory.getLogger(PasscodeService.class);

    private final PasscodeStateRepository passcodeStateRepository;
    private final EventSessionRepository eventSessionRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasscodeService(PasscodeStateRepository passcodeStateRepository,
                           EventSessionRepository eventSessionRepository) {
        this.passcodeStateRepository = passcodeStateRepository;
        this.eventSessionRepository = eventSessionRepository;
    }

    /**
     * Generates a new 6-digit passcode for the given session, replacing any existing one.
     *
     * @param sessionId the session to rotate the passcode for
     * @return the updated passcode state
     */
    public PasscodeState rotatePasscode(String sessionId) {
        log.debug("Rotating passcode for session {}", sessionId);

        String passcode = String.format("%06d", secureRandom.nextInt(1_000_000));
        Instant now = Instant.now();

        PasscodeState state = passcodeStateRepository.findById(sessionId)
                .orElseGet(() -> {
                    PasscodeState newState = new PasscodeState();
                    newState.setSessionId(sessionId);
                    return newState;
                });

        state.setCurrentPasscode(passcode);
        state.setGeneratedAt(now);
        state.setExpiresAt(now.plusSeconds(60));

        state = passcodeStateRepository.save(state);
        log.debug("Passcode rotated for session {}", sessionId);

        return state;
    }

    /**
     * Rotates passcodes for all sessions that are currently active or about to start
     * within 30 minutes.
     */
    public void rotateAllActiveSessionPasscodes() {
        LocalDateTime threshold = LocalDateTime.now().plusMinutes(30);
        List<EventSession> activeSessions = eventSessionRepository.findActiveSessionsForCheckin(threshold);

        log.debug("Rotating passcodes for {} active sessions", activeSessions.size());

        for (EventSession session : activeSessions) {
            rotatePasscode(session.getId());
        }
    }

    /**
     * Returns the current passcode information for a session.
     *
     * @param sessionId the session identifier
     * @return the passcode response DTO with remaining seconds
     * @throws BusinessException if no passcode state exists for the session
     */
    @Transactional(readOnly = true)
    public PasscodeResponse getCurrentPasscode(String sessionId) {
        log.debug("Fetching current passcode for session {}", sessionId);

        PasscodeState state = passcodeStateRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException("Passcode not found", 404, "NOT_FOUND"));

        PasscodeResponse response = new PasscodeResponse();
        response.setSessionId(state.getSessionId());
        response.setPasscode(state.getCurrentPasscode());
        response.setGeneratedAt(state.getGeneratedAt());
        response.setExpiresAt(state.getExpiresAt());
        response.setRemainingSeconds(
                Math.max(0, Duration.between(Instant.now(), state.getExpiresAt()).getSeconds()));

        return response;
    }

    /**
     * Validates a passcode attempt against the current state.
     *
     * @param sessionId the session identifier
     * @param passcode  the passcode to validate
     * @return {@code true} if the passcode matches and has not expired
     */
    @Transactional(readOnly = true)
    public boolean validatePasscode(String sessionId, String passcode) {
        log.debug("Validating passcode for session {}", sessionId);

        PasscodeState state = passcodeStateRepository.findById(sessionId)
                .orElse(null);

        if (state == null) {
            log.debug("No passcode state found for session {}", sessionId);
            return false;
        }

        boolean valid = state.getCurrentPasscode().equals(passcode)
                && Instant.now().isBefore(state.getExpiresAt());

        log.debug("Passcode validation result for session {}: {}", sessionId, valid);
        return valid;
    }
}
