package com.eventops.service;

import com.eventops.audit.logging.AuditService;
import com.eventops.common.exception.BusinessException;
import com.eventops.domain.checkin.CheckInRecord;
import com.eventops.domain.checkin.CheckInStatus;
import com.eventops.domain.checkin.DeviceBinding;
import com.eventops.domain.checkin.PasscodeState;
import com.eventops.domain.event.EventSession;
import com.eventops.domain.event.SessionStatus;
import com.eventops.domain.registration.Registration;
import com.eventops.domain.registration.RegistrationStatus;
import com.eventops.domain.notification.NotificationType;
import com.eventops.repository.checkin.CheckInRecordRepository;
import com.eventops.repository.checkin.DeviceBindingRepository;
import com.eventops.repository.checkin.PasscodeStateRepository;
import com.eventops.repository.event.EventSessionRepository;
import com.eventops.repository.registration.RegistrationRepository;
import com.eventops.service.checkin.CheckInService;
import com.eventops.service.checkin.PasscodeService;
import com.eventops.service.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckInServiceTest {

    @Mock
    private CheckInRecordRepository checkInRecordRepository;

    @Mock
    private DeviceBindingRepository deviceBindingRepository;

    @Mock
    private EventSessionRepository eventSessionRepository;

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private PasscodeService passcodeService;

    @Mock
    private AuditService auditService;

        @Mock
        private NotificationService notificationService;

    @InjectMocks
    private CheckInService checkInService;

    private static final String SESSION_ID = "session-1";
    private static final String USER_ID = "user-1";
    private static final String STAFF_ID = "staff-1";
    private static final String PASSCODE = "123456";
    private static final String DEVICE_TOKEN = "device-token-abc";

    // ---------------------------------------------------------------
    // checkIn() success
    // ---------------------------------------------------------------

    @Test
    void checkIn_success_withinWindow() {
        EventSession session = buildSession(SESSION_ID, LocalDateTime.now().plusMinutes(5));

        when(eventSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(passcodeService.validatePasscode(SESSION_ID, PASSCODE)).thenReturn(true);
        when(registrationRepository.findByUserIdAndSessionId(USER_ID, SESSION_ID)).thenReturn(Optional.of(buildRegistration(RegistrationStatus.CONFIRMED)));
        when(checkInRecordRepository.existsBySessionIdAndUserIdAndStatus(
                SESSION_ID, USER_ID, CheckInStatus.CHECKED_IN)).thenReturn(false);
        when(checkInRecordRepository.save(any(CheckInRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = checkInService.checkIn(SESSION_ID, USER_ID, STAFF_ID, PASSCODE, DEVICE_TOKEN);

        assertNotNull(response);

        ArgumentCaptor<CheckInRecord> captor = ArgumentCaptor.forClass(CheckInRecord.class);
        verify(checkInRecordRepository, atLeastOnce()).save(captor.capture());

        CheckInRecord lastSaved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals(CheckInStatus.CHECKED_IN, lastSaved.getStatus());
        assertEquals(USER_ID, lastSaved.getUserId());
        assertEquals(SESSION_ID, lastSaved.getSessionId());
        assertEquals(DEVICE_TOKEN, lastSaved.getDeviceTokenEncrypted());
        assertNotNull(lastSaved.getDeviceTokenHash());
    }

    // ---------------------------------------------------------------
    // checkIn() denied scenarios
    // ---------------------------------------------------------------

    @Test
    void checkIn_denied_beforeWindow() {
        // Session starts in 60 minutes -- outside the 30-minute before window
        EventSession session = buildSession(SESSION_ID, LocalDateTime.now().plusMinutes(60));

        when(eventSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(checkInRecordRepository.save(any(CheckInRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> checkInService.checkIn(SESSION_ID, USER_ID, STAFF_ID, PASSCODE, DEVICE_TOKEN));

        assertEquals("WINDOW_CLOSED", ex.getErrorCode());
        verify(notificationService).sendNotification(eq(USER_ID), eq(NotificationType.CHECKIN_EXCEPTION),
                anyString(), anyMap());
    }

    @Test
    void checkIn_denied_afterWindow() {
        // Session started 30 minutes ago -- outside the 15-minute after window
        EventSession session = buildSession(SESSION_ID, LocalDateTime.now().minusMinutes(30));

        when(eventSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(checkInRecordRepository.save(any(CheckInRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> checkInService.checkIn(SESSION_ID, USER_ID, STAFF_ID, PASSCODE, DEVICE_TOKEN));

        assertEquals("WINDOW_CLOSED", ex.getErrorCode());
        verify(notificationService).sendNotification(eq(USER_ID), eq(NotificationType.CHECKIN_EXCEPTION),
                anyString(), anyMap());
    }

    @Test
    void checkIn_denied_invalidPasscode() {
        EventSession session = buildSession(SESSION_ID, LocalDateTime.now().plusMinutes(5));

        when(eventSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(passcodeService.validatePasscode(SESSION_ID, PASSCODE)).thenReturn(false);
        when(checkInRecordRepository.save(any(CheckInRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> checkInService.checkIn(SESSION_ID, USER_ID, STAFF_ID, PASSCODE, DEVICE_TOKEN));

        assertEquals("INVALID_PASSCODE", ex.getErrorCode());
        verify(notificationService).sendNotification(eq(USER_ID), eq(NotificationType.CHECKIN_EXCEPTION),
                anyString(), anyMap());
    }

    @Test
    void checkIn_denied_notRegistered() {
        EventSession session = buildSession(SESSION_ID, LocalDateTime.now().plusMinutes(5));

        when(eventSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(passcodeService.validatePasscode(SESSION_ID, PASSCODE)).thenReturn(true);
        when(registrationRepository.findByUserIdAndSessionId(USER_ID, SESSION_ID)).thenReturn(Optional.empty());
        when(checkInRecordRepository.save(any(CheckInRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> checkInService.checkIn(SESSION_ID, USER_ID, STAFF_ID, PASSCODE, DEVICE_TOKEN));

        assertEquals("NOT_REGISTERED", ex.getErrorCode());
    }

    @Test
    void checkIn_denied_duplicate() {
        EventSession session = buildSession(SESSION_ID, LocalDateTime.now().plusMinutes(5));

        when(eventSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(passcodeService.validatePasscode(SESSION_ID, PASSCODE)).thenReturn(true);
        when(registrationRepository.findByUserIdAndSessionId(USER_ID, SESSION_ID)).thenReturn(Optional.of(buildRegistration(RegistrationStatus.CONFIRMED)));
        when(checkInRecordRepository.existsBySessionIdAndUserIdAndStatus(
                SESSION_ID, USER_ID, CheckInStatus.CHECKED_IN)).thenReturn(true);
        when(checkInRecordRepository.save(any(CheckInRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> checkInService.checkIn(SESSION_ID, USER_ID, STAFF_ID, PASSCODE, DEVICE_TOKEN));

        assertEquals("DUPLICATE_CHECKIN", ex.getErrorCode());
    }

    // ---------------------------------------------------------------
    // Device binding
    // ---------------------------------------------------------------

    @Test
    void checkIn_denied_deviceConflict() {
        EventSession session = buildSession(SESSION_ID, LocalDateTime.now().plusMinutes(5));
        session.setDeviceBindingRequired(true);

        DeviceBinding existingBinding = new DeviceBinding();
        existingBinding.setUserId(USER_ID);
        existingBinding.setDeviceTokenHash("different-hash-from-earlier-device");
        existingBinding.setBindingDate(LocalDate.now());

        when(eventSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(passcodeService.validatePasscode(SESSION_ID, PASSCODE)).thenReturn(true);
        when(registrationRepository.findByUserIdAndSessionId(USER_ID, SESSION_ID)).thenReturn(Optional.of(buildRegistration(RegistrationStatus.CONFIRMED)));
        when(checkInRecordRepository.existsBySessionIdAndUserIdAndStatus(
                SESSION_ID, USER_ID, CheckInStatus.CHECKED_IN)).thenReturn(false);
        when(deviceBindingRepository.findByUserIdAndBindingDate(eq(USER_ID), any(LocalDate.class)))
                .thenReturn(Optional.of(existingBinding));
        when(checkInRecordRepository.save(any(CheckInRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> checkInService.checkIn(SESSION_ID, USER_ID, STAFF_ID, PASSCODE, DEVICE_TOKEN));

        assertEquals("DEVICE_CONFLICT", ex.getErrorCode());
        verify(notificationService).sendNotification(eq(USER_ID), eq(NotificationType.CHECKIN_DEVICE_WARNING),
                anyString(), anyMap());
    }

    @Test
    void checkIn_createsBinding_whenNewDevice() {
        EventSession session = buildSession(SESSION_ID, LocalDateTime.now().plusMinutes(5));
        session.setDeviceBindingRequired(true);

        when(eventSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(passcodeService.validatePasscode(SESSION_ID, PASSCODE)).thenReturn(true);
        when(registrationRepository.findByUserIdAndSessionId(USER_ID, SESSION_ID)).thenReturn(Optional.of(buildRegistration(RegistrationStatus.CONFIRMED)));
        when(checkInRecordRepository.existsBySessionIdAndUserIdAndStatus(
                SESSION_ID, USER_ID, CheckInStatus.CHECKED_IN)).thenReturn(false);
        when(deviceBindingRepository.findByUserIdAndBindingDate(eq(USER_ID), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(deviceBindingRepository.save(any(DeviceBinding.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(checkInRecordRepository.save(any(CheckInRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        checkInService.checkIn(SESSION_ID, USER_ID, STAFF_ID, PASSCODE, DEVICE_TOKEN);

        ArgumentCaptor<DeviceBinding> captor = ArgumentCaptor.forClass(DeviceBinding.class);
        verify(deviceBindingRepository).save(captor.capture());

        DeviceBinding savedBinding = captor.getValue();
        assertEquals(USER_ID, savedBinding.getUserId());
        assertNotNull(savedBinding.getDeviceTokenHash());
        assertEquals(DEVICE_TOKEN, savedBinding.getDeviceTokenEncrypted());
        assertEquals(LocalDate.now(), savedBinding.getBindingDate());
    }

    @Test
    void checkIn_rejectsMissingDeviceToken_whenBindingRequired() {
        EventSession session = buildSession(SESSION_ID, LocalDateTime.now().plusMinutes(5));
        session.setDeviceBindingRequired(true);

        when(eventSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(passcodeService.validatePasscode(SESSION_ID, PASSCODE)).thenReturn(true);
        when(registrationRepository.findByUserIdAndSessionId(USER_ID, SESSION_ID)).thenReturn(Optional.of(buildRegistration(RegistrationStatus.CONFIRMED)));
        when(checkInRecordRepository.existsBySessionIdAndUserIdAndStatus(
                SESSION_ID, USER_ID, CheckInStatus.CHECKED_IN)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> checkInService.checkIn(SESSION_ID, USER_ID, STAFF_ID, PASSCODE, "   "));

        assertEquals("DEVICE_TOKEN_REQUIRED", ex.getErrorCode());
        verify(deviceBindingRepository, never()).save(any());
        verify(checkInRecordRepository, never()).save(any(CheckInRecord.class));
    }

    @Test
    void checkIn_denied_concurrentDevice() {
        EventSession session = buildSession(SESSION_ID, LocalDateTime.now().plusMinutes(5));

        CheckInRecord prior = new CheckInRecord();
        prior.setSessionId("session-other");
        prior.setUserId(USER_ID);
        prior.setStatus(CheckInStatus.CHECKED_IN);
        prior.setDeviceTokenHash("previous-device-hash");
        prior.setCheckedInAt(Instant.now().minusSeconds(60));

        when(eventSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(passcodeService.validatePasscode(SESSION_ID, PASSCODE)).thenReturn(true);
        when(registrationRepository.findByUserIdAndSessionId(USER_ID, SESSION_ID)).thenReturn(Optional.of(buildRegistration(RegistrationStatus.CONFIRMED)));
        when(checkInRecordRepository.existsBySessionIdAndUserIdAndStatus(
                SESSION_ID, USER_ID, CheckInStatus.CHECKED_IN)).thenReturn(false);
        when(checkInRecordRepository.findTopByUserIdAndStatusAndSessionIdNotOrderByCheckedInAtDesc(
                USER_ID, CheckInStatus.CHECKED_IN, SESSION_ID)).thenReturn(Optional.of(prior));
        when(checkInRecordRepository.save(any(CheckInRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> checkInService.checkIn(SESSION_ID, USER_ID, STAFF_ID, PASSCODE, DEVICE_TOKEN));

        assertEquals("CONCURRENT_DEVICE", ex.getErrorCode());
        verify(notificationService).sendNotification(eq(USER_ID), eq(NotificationType.CHECKIN_DEVICE_WARNING),
                anyString(), anyMap());
    }

    @Test
    void checkIn_allowsDifferentDevice_whenOutsideConcurrentWindow() {
        EventSession session = buildSession(SESSION_ID, LocalDateTime.now().plusMinutes(5));

        CheckInRecord prior = new CheckInRecord();
        prior.setSessionId("session-other");
        prior.setUserId(USER_ID);
        prior.setStatus(CheckInStatus.CHECKED_IN);
        prior.setDeviceTokenHash("previous-device-hash");
        prior.setCheckedInAt(Instant.now().minusSeconds(600));

        when(eventSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(passcodeService.validatePasscode(SESSION_ID, PASSCODE)).thenReturn(true);
        when(registrationRepository.findByUserIdAndSessionId(USER_ID, SESSION_ID)).thenReturn(Optional.of(buildRegistration(RegistrationStatus.CONFIRMED)));
        when(checkInRecordRepository.existsBySessionIdAndUserIdAndStatus(
                SESSION_ID, USER_ID, CheckInStatus.CHECKED_IN)).thenReturn(false);
        when(checkInRecordRepository.findTopByUserIdAndStatusAndSessionIdNotOrderByCheckedInAtDesc(
                USER_ID, CheckInStatus.CHECKED_IN, SESSION_ID)).thenReturn(Optional.of(prior));
        when(checkInRecordRepository.save(any(CheckInRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() ->
                checkInService.checkIn(SESSION_ID, USER_ID, STAFF_ID, PASSCODE, DEVICE_TOKEN));
    }

    // ---------------------------------------------------------------
    // Passcode validation (tests PasscodeService directly)
    // ---------------------------------------------------------------

    @Test
    void passcodeValidation_rejectsExpired() {
        PasscodeStateRepository passcodeStateRepo = mock(PasscodeStateRepository.class);
        EventSessionRepository sessionRepo = mock(EventSessionRepository.class);
        PasscodeService directPasscodeService = new PasscodeService(passcodeStateRepo, sessionRepo);

        PasscodeState state = new PasscodeState();
        state.setSessionId(SESSION_ID);
        state.setCurrentPasscode("654321");
        state.setGeneratedAt(Instant.now().minusSeconds(120));
        state.setExpiresAt(Instant.now().minusSeconds(60)); // already expired

        when(passcodeStateRepo.findById(SESSION_ID)).thenReturn(Optional.of(state));

        boolean result = directPasscodeService.validatePasscode(SESSION_ID, "654321");
        assertFalse(result, "Expired passcode should be rejected");
    }

    // ---------------------------------------------------------------
    // Boundary test
    // ---------------------------------------------------------------

    @Test
    void checkInWindow_boundaryExact() {
        // Session starts exactly 30 minutes from now -- boundary is inclusive
        EventSession session = buildSession(SESSION_ID, LocalDateTime.now().plusMinutes(30));

        when(eventSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(passcodeService.validatePasscode(SESSION_ID, PASSCODE)).thenReturn(true);
        when(registrationRepository.findByUserIdAndSessionId(USER_ID, SESSION_ID)).thenReturn(Optional.of(buildRegistration(RegistrationStatus.CONFIRMED)));
        when(checkInRecordRepository.existsBySessionIdAndUserIdAndStatus(
                SESSION_ID, USER_ID, CheckInStatus.CHECKED_IN)).thenReturn(false);
        when(checkInRecordRepository.save(any(CheckInRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Should not throw -- the boundary is inclusive (windowOpen = startTime - 30min = now)
        assertDoesNotThrow(
                () -> checkInService.checkIn(SESSION_ID, USER_ID, STAFF_ID, PASSCODE, DEVICE_TOKEN));
    }

    // ---------------------------------------------------------------
    // Registration status eligibility
    // ---------------------------------------------------------------

    @Test
    void checkIn_denied_waitlistedRegistration() {
        EventSession session = buildSession(SESSION_ID, LocalDateTime.now().plusMinutes(5));

        when(eventSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(passcodeService.validatePasscode(SESSION_ID, PASSCODE)).thenReturn(true);
        when(registrationRepository.findByUserIdAndSessionId(USER_ID, SESSION_ID))
                .thenReturn(Optional.of(buildRegistration(RegistrationStatus.WAITLISTED)));
        when(checkInRecordRepository.save(any(CheckInRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> checkInService.checkIn(SESSION_ID, USER_ID, STAFF_ID, PASSCODE, DEVICE_TOKEN));

        assertEquals("INELIGIBLE_STATUS", ex.getErrorCode());
    }

    @Test
    void checkIn_denied_cancelledRegistration() {
        EventSession session = buildSession(SESSION_ID, LocalDateTime.now().plusMinutes(5));

        when(eventSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(passcodeService.validatePasscode(SESSION_ID, PASSCODE)).thenReturn(true);
        when(registrationRepository.findByUserIdAndSessionId(USER_ID, SESSION_ID))
                .thenReturn(Optional.of(buildRegistration(RegistrationStatus.CANCELLED)));
        when(checkInRecordRepository.save(any(CheckInRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> checkInService.checkIn(SESSION_ID, USER_ID, STAFF_ID, PASSCODE, DEVICE_TOKEN));

        assertEquals("INELIGIBLE_STATUS", ex.getErrorCode());
    }

    @Test
    void checkIn_denied_expiredRegistration() {
        EventSession session = buildSession(SESSION_ID, LocalDateTime.now().plusMinutes(5));

        when(eventSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(passcodeService.validatePasscode(SESSION_ID, PASSCODE)).thenReturn(true);
        when(registrationRepository.findByUserIdAndSessionId(USER_ID, SESSION_ID))
                .thenReturn(Optional.of(buildRegistration(RegistrationStatus.EXPIRED)));
        when(checkInRecordRepository.save(any(CheckInRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> checkInService.checkIn(SESSION_ID, USER_ID, STAFF_ID, PASSCODE, DEVICE_TOKEN));

        assertEquals("INELIGIBLE_STATUS", ex.getErrorCode());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private Registration buildRegistration(RegistrationStatus status) {
        Registration registration = new Registration();
        registration.setUserId(USER_ID);
        registration.setSessionId(SESSION_ID);
        registration.setStatus(status);
        return registration;
    }

    private EventSession buildSession(String id, LocalDateTime startTime) {
        EventSession session = new EventSession();
        session.setId(id);
        session.setTitle("Test Session");
        session.setStartTime(startTime);
        session.setEndTime(startTime.plusHours(2));
        session.setMaxCapacity(100);
        session.setCurrentRegistrations(0);
        session.setStatus(SessionStatus.OPEN_FOR_REGISTRATION);
        session.setCheckinWindowBeforeMinutes(30);
        session.setCheckinWindowAfterMinutes(15);
        session.setDeviceBindingRequired(false);
        session.setLocation("Room B");
        return session;
    }
}
