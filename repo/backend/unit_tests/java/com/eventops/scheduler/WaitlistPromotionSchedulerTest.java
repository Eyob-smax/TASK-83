package com.eventops.scheduler;

import com.eventops.domain.event.EventSession;
import com.eventops.domain.event.SessionStatus;
import com.eventops.repository.event.EventSessionRepository;
import com.eventops.service.registration.RegistrationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WaitlistPromotionSchedulerTest {

    @Mock RegistrationService registrationService;
    @Mock EventSessionRepository eventSessionRepository;
    @InjectMocks WaitlistPromotionScheduler scheduler;

    private EventSession session(String id, int max, int current) {
        EventSession s = new EventSession();
        s.setId(id);
        s.setMaxCapacity(max);
        s.setCurrentRegistrations(current);
        s.setStatus(SessionStatus.OPEN_FOR_REGISTRATION);
        return s;
    }

    @Test
    void reconcileWaitlists_whenNoOpenSessions_noopsQuietly() {
        when(eventSessionRepository.findByStatus(SessionStatus.OPEN_FOR_REGISTRATION))
                .thenReturn(List.of());
        scheduler.reconcileWaitlists();
        verifyNoInteractions(registrationService);
    }

    @Test
    void reconcileWaitlists_sessionsWithCapacityArePromoted() {
        when(eventSessionRepository.findByStatus(SessionStatus.OPEN_FOR_REGISTRATION))
                .thenReturn(List.of(session("s1", 10, 5), session("s2", 10, 10)));

        scheduler.reconcileWaitlists();

        // Only s1 is not full; s2 is full
        verify(registrationService).promoteNextWaitlisted("s1");
        verify(registrationService, never()).promoteNextWaitlisted("s2");
    }

    @Test
    void reconcileWaitlists_continuesAfterExceptionOnOneSession() {
        EventSession s1 = session("s1", 10, 5);
        EventSession s2 = session("s2", 10, 5);
        when(eventSessionRepository.findByStatus(SessionStatus.OPEN_FOR_REGISTRATION))
                .thenReturn(List.of(s1, s2));
        doThrow(new RuntimeException("db")).when(registrationService).promoteNextWaitlisted("s1");

        assertDoesNotThrow(scheduler::reconcileWaitlists);

        verify(registrationService).promoteNextWaitlisted("s1");
        verify(registrationService).promoteNextWaitlisted("s2");
    }

    @Test
    void reconcileWaitlists_skipsFullSessions() {
        when(eventSessionRepository.findByStatus(SessionStatus.OPEN_FOR_REGISTRATION))
                .thenReturn(List.of(session("full", 5, 5)));

        scheduler.reconcileWaitlists();

        verifyNoInteractions(registrationService);
    }
}
