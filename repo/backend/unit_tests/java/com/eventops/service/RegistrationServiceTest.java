package com.eventops.service;

import com.eventops.audit.logging.AuditService;
import com.eventops.common.exception.BusinessException;
import com.eventops.domain.event.EventSession;
import com.eventops.domain.event.SessionStatus;
import com.eventops.domain.registration.Registration;
import com.eventops.domain.registration.RegistrationStatus;
import com.eventops.repository.event.EventSessionRepository;
import com.eventops.repository.registration.RegistrationRepository;
import com.eventops.service.notification.NotificationService;
import com.eventops.service.registration.RegistrationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private EventSessionRepository eventSessionRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private RegistrationService registrationService;

    // ---------------------------------------------------------------
    // register()
    // ---------------------------------------------------------------

    @Test
    void register_confirmed_whenCapacityAvailable() {
        String userId = "user-1";
        String sessionId = "session-1";

        EventSession session = buildSession(sessionId, 10, 0, SessionStatus.OPEN_FOR_REGISTRATION);

        when(eventSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(registrationRepository.existsByUserIdAndSessionId(userId, sessionId)).thenReturn(false);
        when(registrationRepository.countActiveRegistrations(sessionId)).thenReturn(5L);
        when(registrationRepository.save(any(Registration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(eventSessionRepository.save(any(EventSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = registrationService.register(userId, sessionId);

        assertEquals("CONFIRMED", response.getStatus());

        ArgumentCaptor<Registration> regCaptor = ArgumentCaptor.forClass(Registration.class);
        verify(registrationRepository).save(regCaptor.capture());
        assertEquals(RegistrationStatus.CONFIRMED, regCaptor.getValue().getStatus());

        ArgumentCaptor<EventSession> sessionCaptor = ArgumentCaptor.forClass(EventSession.class);
        verify(eventSessionRepository).save(sessionCaptor.capture());
        assertEquals(6, sessionCaptor.getValue().getCurrentRegistrations());

        verify(auditService).logForCurrentUser(any(), eq("Registration"), any(), any());
    }

    @Test
    void register_waitlisted_whenSessionFull() {
        String userId = "user-2";
        String sessionId = "session-2";

        EventSession session = buildSession(sessionId, 10, 10, SessionStatus.OPEN_FOR_REGISTRATION);

        when(eventSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(registrationRepository.existsByUserIdAndSessionId(userId, sessionId)).thenReturn(false);
        when(registrationRepository.countActiveRegistrations(sessionId)).thenReturn(10L);
        when(registrationRepository.findWaitlistedBySessionOrderByPosition(sessionId))
                .thenReturn(Collections.emptyList());
        when(registrationRepository.save(any(Registration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = registrationService.register(userId, sessionId);

        assertEquals("WAITLISTED", response.getStatus());
        assertEquals(1, response.getWaitlistPosition());
    }

    @Test
    void register_throws409_whenDuplicate() {
        String userId = "user-3";
        String sessionId = "session-3";

        EventSession session = buildSession(sessionId, 10, 5, SessionStatus.OPEN_FOR_REGISTRATION);

        when(eventSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(registrationRepository.existsByUserIdAndSessionId(userId, sessionId)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> registrationService.register(userId, sessionId));

        assertEquals("DUPLICATE_REGISTRATION", ex.getErrorCode());
        assertEquals(409, ex.getHttpStatus());
    }

    @Test
    void register_throws404_whenSessionNotFound() {
        when(eventSessionRepository.findById("missing")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> registrationService.register("user-4", "missing"));

        assertEquals("NOT_FOUND", ex.getErrorCode());
        assertEquals(404, ex.getHttpStatus());
    }

    @Test
    void register_throws422_whenSessionNotOpen() {
        String sessionId = "session-draft";
        EventSession session = buildSession(sessionId, 10, 0, SessionStatus.DRAFT);

        when(eventSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> registrationService.register("user-5", sessionId));

        assertEquals("SESSION_NOT_OPEN", ex.getErrorCode());
        assertEquals(422, ex.getHttpStatus());
    }

    // ---------------------------------------------------------------
    // cancel()
    // ---------------------------------------------------------------

    @Test
    void cancel_decrementsAndTriggersPromotion() {
        String userId = "user-6";
        String registrationId = "reg-6";
        String sessionId = "session-6";

        Registration registration = new Registration();
        registration.setId(registrationId);
        registration.setUserId(userId);
        registration.setSessionId(sessionId);
        registration.setStatus(RegistrationStatus.CONFIRMED);

        EventSession session = buildSession(sessionId, 10, 5, SessionStatus.FULL);

        when(registrationRepository.findById(registrationId)).thenReturn(Optional.of(registration));
        when(eventSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(registrationRepository.save(any(Registration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(eventSessionRepository.save(any(EventSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(registrationRepository.countActiveRegistrations(sessionId)).thenReturn(4L);
        // For promoteNextWaitlisted called internally
        when(registrationRepository.findWaitlistedBySessionOrderByPosition(sessionId))
                .thenReturn(Collections.emptyList());

        registrationService.cancel(userId, registrationId);

        ArgumentCaptor<Registration> regCaptor = ArgumentCaptor.forClass(Registration.class);
        verify(registrationRepository, atLeastOnce()).save(regCaptor.capture());
        Registration saved = regCaptor.getAllValues().get(0);
        assertEquals(RegistrationStatus.CANCELLED, saved.getStatus());
        assertNotNull(saved.getCancelledAt());

        ArgumentCaptor<EventSession> sessionCaptor = ArgumentCaptor.forClass(EventSession.class);
        verify(eventSessionRepository, atLeastOnce()).save(sessionCaptor.capture());
        assertEquals(4, sessionCaptor.getValue().getCurrentRegistrations());
    }

    // ---------------------------------------------------------------
    // promoteNextWaitlisted()
    // ---------------------------------------------------------------

    @Test
    void promoteNextWaitlisted_promotesFirst_whenBeforeCutoff() {
        String sessionId = "session-7";
        EventSession session = buildSession(sessionId, 10, 8, SessionStatus.OPEN_FOR_REGISTRATION);
        session.setWaitlistPromotionCutoff(LocalDateTime.now().plusDays(1));

        Registration wait1 = new Registration();
        wait1.setId("wait-1");
        wait1.setUserId("user-w1");
        wait1.setSessionId(sessionId);
        wait1.setStatus(RegistrationStatus.WAITLISTED);
        wait1.setWaitlistPosition(1);

        Registration wait2 = new Registration();
        wait2.setId("wait-2");
        wait2.setUserId("user-w2");
        wait2.setSessionId(sessionId);
        wait2.setStatus(RegistrationStatus.WAITLISTED);
        wait2.setWaitlistPosition(2);

        when(eventSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(registrationRepository.findWaitlistedBySessionOrderByPosition(sessionId))
                .thenReturn(List.of(wait1, wait2));
        when(registrationRepository.countActiveRegistrations(sessionId)).thenReturn(9L);
        when(registrationRepository.save(any(Registration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(eventSessionRepository.save(any(EventSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        registrationService.promoteNextWaitlisted(sessionId);

        ArgumentCaptor<Registration> captor = ArgumentCaptor.forClass(Registration.class);
        verify(registrationRepository).save(captor.capture());
        Registration promoted = captor.getValue();
        assertEquals(RegistrationStatus.PROMOTED, promoted.getStatus());
        assertNotNull(promoted.getPromotedAt());
        assertNull(promoted.getWaitlistPosition());
        assertEquals("wait-1", promoted.getId());
    }

    @Test
    void promoteNextWaitlisted_expiresAll_whenAfterCutoff() {
        String sessionId = "session-8";
        EventSession session = buildSession(sessionId, 10, 8, SessionStatus.OPEN_FOR_REGISTRATION);
        session.setWaitlistPromotionCutoff(LocalDateTime.now().minusDays(1));

        Registration wait1 = new Registration();
        wait1.setId("wait-1");
        wait1.setUserId("user-w1");
        wait1.setSessionId(sessionId);
        wait1.setStatus(RegistrationStatus.WAITLISTED);

        Registration wait2 = new Registration();
        wait2.setId("wait-2");
        wait2.setUserId("user-w2");
        wait2.setSessionId(sessionId);
        wait2.setStatus(RegistrationStatus.WAITLISTED);

        when(eventSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(registrationRepository.findWaitlistedBySessionOrderByPosition(sessionId))
                .thenReturn(List.of(wait1, wait2));
        when(registrationRepository.save(any(Registration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        registrationService.promoteNextWaitlisted(sessionId);

        ArgumentCaptor<Registration> captor = ArgumentCaptor.forClass(Registration.class);
        verify(registrationRepository, times(2)).save(captor.capture());

        for (Registration reg : captor.getAllValues()) {
            assertEquals(RegistrationStatus.EXPIRED, reg.getStatus());
        }
    }

    @Test
    void promoteNextWaitlisted_noop_whenSessionFull() {
        String sessionId = "session-9";
        EventSession session = buildSession(sessionId, 10, 10, SessionStatus.FULL);

        when(eventSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        registrationService.promoteNextWaitlisted(sessionId);

        verify(registrationRepository, never()).save(any());
    }

    @Test
    void promoteNextWaitlisted_noop_whenNoWaitlisted() {
        String sessionId = "session-10";
        EventSession session = buildSession(sessionId, 10, 8, SessionStatus.OPEN_FOR_REGISTRATION);

        when(eventSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(registrationRepository.findWaitlistedBySessionOrderByPosition(sessionId))
                .thenReturn(Collections.emptyList());

        registrationService.promoteNextWaitlisted(sessionId);

        verify(registrationRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------

    private EventSession buildSession(String id, int maxCapacity, int currentRegistrations,
                                      SessionStatus status) {
        EventSession session = new EventSession();
        session.setId(id);
        session.setTitle("Test Session");
        session.setMaxCapacity(maxCapacity);
        session.setCurrentRegistrations(currentRegistrations);
        session.setStatus(status);
        session.setLocation("Room A");
        session.setStartTime(LocalDateTime.now().plusDays(7));
        session.setEndTime(LocalDateTime.now().plusDays(7).plusHours(2));
        return session;
    }
}
