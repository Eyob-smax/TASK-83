package com.eventops.service;

import com.eventops.common.dto.checkin.PasscodeResponse;
import com.eventops.common.exception.BusinessException;
import com.eventops.domain.checkin.PasscodeState;
import com.eventops.repository.checkin.PasscodeStateRepository;
import com.eventops.repository.event.EventSessionRepository;
import com.eventops.service.checkin.PasscodeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PasscodeService}: passcode generation, validation,
 * and current-passcode retrieval.
 */
@ExtendWith(MockitoExtension.class)
class PasscodeServiceTest {

    @Mock
    private PasscodeStateRepository passcodeStateRepository;

    @Mock
    private EventSessionRepository eventSessionRepository;

    @InjectMocks
    private PasscodeService passcodeService;

    // ------------------------------------------------------------------
    // rotatePasscode()
    // ------------------------------------------------------------------

    @Test
    void rotatePasscode_generates6Digits() {
        String sessionId = "session-1";

        when(passcodeStateRepository.findById(sessionId)).thenReturn(Optional.empty());
        when(passcodeStateRepository.save(any(PasscodeState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PasscodeState result = passcodeService.rotatePasscode(sessionId);

        assertNotNull(result);
        assertNotNull(result.getCurrentPasscode());
        // Verify passcode is exactly 6 digits
        assertTrue(result.getCurrentPasscode().matches("\\d{6}"),
                "Passcode should be exactly 6 digits, got: " + result.getCurrentPasscode());
    }

    @Test
    void rotatePasscode_storesInRepository() {
        String sessionId = "session-2";

        when(passcodeStateRepository.findById(sessionId)).thenReturn(Optional.empty());
        when(passcodeStateRepository.save(any(PasscodeState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        passcodeService.rotatePasscode(sessionId);

        ArgumentCaptor<PasscodeState> captor = ArgumentCaptor.forClass(PasscodeState.class);
        verify(passcodeStateRepository).save(captor.capture());

        PasscodeState saved = captor.getValue();
        assertEquals(sessionId, saved.getSessionId());
        assertNotNull(saved.getCurrentPasscode());
        assertNotNull(saved.getGeneratedAt());
        assertNotNull(saved.getExpiresAt());
        assertTrue(saved.getExpiresAt().isAfter(saved.getGeneratedAt()));
    }

    @Test
    void rotatePasscode_setsExpiryTo60Seconds() {
        String sessionId = "session-3";

        when(passcodeStateRepository.findById(sessionId)).thenReturn(Optional.empty());
        when(passcodeStateRepository.save(any(PasscodeState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PasscodeState result = passcodeService.rotatePasscode(sessionId);

        // Expiry should be approximately 60 seconds after generation
        long diffSeconds = result.getExpiresAt().getEpochSecond()
                - result.getGeneratedAt().getEpochSecond();
        assertEquals(60, diffSeconds);
    }

    @Test
    void rotatePasscode_updatesExistingState() {
        String sessionId = "session-4";
        PasscodeState existingState = new PasscodeState();
        existingState.setSessionId(sessionId);
        existingState.setCurrentPasscode("111111");
        existingState.setGeneratedAt(Instant.now().minusSeconds(120));
        existingState.setExpiresAt(Instant.now().minusSeconds(60));

        when(passcodeStateRepository.findById(sessionId)).thenReturn(Optional.of(existingState));
        when(passcodeStateRepository.save(any(PasscodeState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PasscodeState result = passcodeService.rotatePasscode(sessionId);

        assertNotEquals("111111", result.getCurrentPasscode());
        assertEquals(sessionId, result.getSessionId());

        // Should update the same state, not create a new one
        verify(passcodeStateRepository).save(existingState);
    }

    // ------------------------------------------------------------------
    // validatePasscode()
    // ------------------------------------------------------------------

    @Test
    void validatePasscode_returnsTrue_whenMatchAndNotExpired() {
        String sessionId = "session-5";
        PasscodeState state = new PasscodeState();
        state.setSessionId(sessionId);
        state.setCurrentPasscode("123456");
        state.setGeneratedAt(Instant.now().minusSeconds(10));
        state.setExpiresAt(Instant.now().plusSeconds(50));

        when(passcodeStateRepository.findById(sessionId)).thenReturn(Optional.of(state));

        assertTrue(passcodeService.validatePasscode(sessionId, "123456"));
    }

    @Test
    void validatePasscode_returnsFalse_whenExpired() {
        String sessionId = "session-6";
        PasscodeState state = new PasscodeState();
        state.setSessionId(sessionId);
        state.setCurrentPasscode("654321");
        state.setGeneratedAt(Instant.now().minusSeconds(120));
        state.setExpiresAt(Instant.now().minusSeconds(60)); // already expired

        when(passcodeStateRepository.findById(sessionId)).thenReturn(Optional.of(state));

        assertFalse(passcodeService.validatePasscode(sessionId, "654321"));
    }

    @Test
    void validatePasscode_returnsFalse_whenMismatch() {
        String sessionId = "session-7";
        PasscodeState state = new PasscodeState();
        state.setSessionId(sessionId);
        state.setCurrentPasscode("999999");
        state.setGeneratedAt(Instant.now().minusSeconds(10));
        state.setExpiresAt(Instant.now().plusSeconds(50));

        when(passcodeStateRepository.findById(sessionId)).thenReturn(Optional.of(state));

        assertFalse(passcodeService.validatePasscode(sessionId, "000000"));
    }

    @Test
    void validatePasscode_returnsFalse_whenNoStateExists() {
        when(passcodeStateRepository.findById("nonexistent")).thenReturn(Optional.empty());

        assertFalse(passcodeService.validatePasscode("nonexistent", "123456"));
    }

    // ------------------------------------------------------------------
    // getCurrentPasscode()
    // ------------------------------------------------------------------

    @Test
    void getCurrentPasscode_calculatesRemaining() {
        String sessionId = "session-8";
        PasscodeState state = new PasscodeState();
        state.setSessionId(sessionId);
        state.setCurrentPasscode("555555");
        state.setGeneratedAt(Instant.now().minusSeconds(10));
        state.setExpiresAt(Instant.now().plusSeconds(50));

        when(passcodeStateRepository.findById(sessionId)).thenReturn(Optional.of(state));

        PasscodeResponse response = passcodeService.getCurrentPasscode(sessionId);

        assertNotNull(response);
        assertEquals(sessionId, response.getSessionId());
        assertEquals("555555", response.getPasscode());
        assertTrue(response.getRemainingSeconds() > 0,
                "Remaining seconds should be positive");
        assertTrue(response.getRemainingSeconds() <= 50,
                "Remaining seconds should not exceed 50");
    }

    @Test
    void getCurrentPasscode_notFound_throwsException() {
        when(passcodeStateRepository.findById("missing")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> passcodeService.getCurrentPasscode("missing"));

        assertEquals("NOT_FOUND", ex.getErrorCode());
        assertEquals(404, ex.getHttpStatus());
    }

    @Test
    void getCurrentPasscode_expired_remainingIsZero() {
        String sessionId = "session-9";
        PasscodeState state = new PasscodeState();
        state.setSessionId(sessionId);
        state.setCurrentPasscode("777777");
        state.setGeneratedAt(Instant.now().minusSeconds(120));
        state.setExpiresAt(Instant.now().minusSeconds(60)); // already expired

        when(passcodeStateRepository.findById(sessionId)).thenReturn(Optional.of(state));

        PasscodeResponse response = passcodeService.getCurrentPasscode(sessionId);

        assertEquals(0, response.getRemainingSeconds());
    }
}
