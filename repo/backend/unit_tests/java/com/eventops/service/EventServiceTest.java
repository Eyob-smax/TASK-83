package com.eventops.service;

import com.eventops.common.dto.event.EventSessionResponse;
import com.eventops.common.exception.BusinessException;
import com.eventops.domain.event.EventSession;
import com.eventops.domain.event.SessionStatus;
import com.eventops.repository.event.EventSessionRepository;
import com.eventops.service.event.EventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EventService}: session listing, retrieval, and availability.
 */
@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventSessionRepository eventSessionRepository;

    @InjectMocks
    private EventService eventService;

    // ------------------------------------------------------------------
    // listSessions()
    // ------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void listSessions_returnsPagedResults() {
        Pageable pageable = PageRequest.of(0, 10);

        EventSession session1 = buildSession("s-1", "Java Workshop", 50, 20);
        EventSession session2 = buildSession("s-2", "Python Training", 30, 10);

        Page<EventSession> mockPage = new PageImpl<>(
                List.of(session1, session2), pageable, 2);

        when(eventSessionRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(mockPage);

        Page<EventSessionResponse> result = eventService.listSessions(pageable, null, null);

        assertNotNull(result);
        assertEquals(2, result.getContent().size());
        assertEquals("Java Workshop", result.getContent().get(0).getTitle());
        assertEquals("Python Training", result.getContent().get(1).getTitle());

        verify(eventSessionRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listSessions_withStatusFilter_delegatesToSpec() {
        Pageable pageable = PageRequest.of(0, 10);

        EventSession session = buildSession("s-3", "Filtered Session", 100, 50);
        Page<EventSession> mockPage = new PageImpl<>(List.of(session), pageable, 1);

        when(eventSessionRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(mockPage);

        Page<EventSessionResponse> result = eventService.listSessions(
                pageable, "OPEN_FOR_REGISTRATION", null);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        verify(eventSessionRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listSessions_withSearchFilter() {
        Pageable pageable = PageRequest.of(0, 10);

        EventSession session = buildSession("s-4", "Advanced Java", 40, 15);
        Page<EventSession> mockPage = new PageImpl<>(List.of(session), pageable, 1);

        when(eventSessionRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(mockPage);

        Page<EventSessionResponse> result = eventService.listSessions(
                pageable, null, "Java");

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("Advanced Java", result.getContent().get(0).getTitle());
    }

    // ------------------------------------------------------------------
    // getSession()
    // ------------------------------------------------------------------

    @Test
    void getSession_notFound_throws404() {
        when(eventSessionRepository.findById("missing")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> eventService.getSession("missing"));

        assertEquals("NOT_FOUND", ex.getErrorCode());
        assertEquals(404, ex.getHttpStatus());
    }

    @Test
    void getSession_returnsWithRemainingSeats() {
        EventSession session = buildSession("s-5", "Capacity Test", 50, 30);

        when(eventSessionRepository.findById("s-5")).thenReturn(Optional.of(session));

        EventSessionResponse response = eventService.getSession("s-5");

        assertNotNull(response);
        assertEquals("s-5", response.getId());
        assertEquals("Capacity Test", response.getTitle());
        assertEquals(50, response.getMaxCapacity());
        assertEquals(30, response.getCurrentRegistrations());
        assertEquals(20, response.getRemainingSeats());
    }

    @Test
    void getSession_fullSession_zeroRemainingSeats() {
        EventSession session = buildSession("s-6", "Full Session", 25, 25);

        when(eventSessionRepository.findById("s-6")).thenReturn(Optional.of(session));

        EventSessionResponse response = eventService.getSession("s-6");

        assertEquals(0, response.getRemainingSeats());
        assertEquals(25, response.getMaxCapacity());
        assertEquals(25, response.getCurrentRegistrations());
    }

    @Test
    void getSession_mapsAllFields() {
        EventSession session = buildSession("s-7", "Full Mapping", 100, 42);
        session.setDescription("A detailed description");
        session.setDeviceBindingRequired(true);
        session.setWaitlistPromotionCutoff(LocalDateTime.of(2026, 6, 15, 12, 0));

        when(eventSessionRepository.findById("s-7")).thenReturn(Optional.of(session));

        EventSessionResponse response = eventService.getSession("s-7");

        assertEquals("s-7", response.getId());
        assertEquals("Full Mapping", response.getTitle());
        assertEquals("A detailed description", response.getDescription());
        assertEquals("Room A", response.getLocation());
        assertEquals(100, response.getMaxCapacity());
        assertEquals(42, response.getCurrentRegistrations());
        assertEquals(58, response.getRemainingSeats());
        assertEquals("OPEN_FOR_REGISTRATION", response.getStatus());
        assertTrue(response.isDeviceBindingRequired());
        assertEquals(LocalDateTime.of(2026, 6, 15, 12, 0),
                response.getWaitlistPromotionCutoff());
    }

    // ------------------------------------------------------------------
    // getAvailability()
    // ------------------------------------------------------------------

    @Test
    void getAvailability_delegatesToGetSession() {
        EventSession session = buildSession("s-8", "Availability Test", 50, 30);

        when(eventSessionRepository.findById("s-8")).thenReturn(Optional.of(session));

        EventSessionResponse response = eventService.getAvailability("s-8");

        assertNotNull(response);
        assertEquals("s-8", response.getId());
        assertEquals(50, response.getMaxCapacity());
        assertEquals(30, response.getCurrentRegistrations());
        assertEquals(20, response.getRemainingSeats());

        // getAvailability uses findById just like getSession
        verify(eventSessionRepository).findById("s-8");
    }

    @Test
    void getAvailability_notFound_throws404() {
        when(eventSessionRepository.findById("missing")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> eventService.getAvailability("missing"));

        assertEquals("NOT_FOUND", ex.getErrorCode());
        assertEquals(404, ex.getHttpStatus());
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    private EventSession buildSession(String id, String title, int maxCapacity,
                                       int currentRegistrations) {
        EventSession session = new EventSession();
        session.setId(id);
        session.setTitle(title);
        session.setDescription("Test description");
        session.setLocation("Room A");
        session.setStartTime(LocalDateTime.now().plusDays(7));
        session.setEndTime(LocalDateTime.now().plusDays(7).plusHours(2));
        session.setMaxCapacity(maxCapacity);
        session.setCurrentRegistrations(currentRegistrations);
        session.setStatus(SessionStatus.OPEN_FOR_REGISTRATION);
        return session;
    }
}
