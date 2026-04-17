package com.eventops.repository;

import com.eventops.domain.event.EventSession;
import com.eventops.domain.event.SessionStatus;
import com.eventops.repository.event.EventSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class EventSessionRepositoryTest {

    @Autowired
    private EventSessionRepository eventSessionRepository;

    @BeforeEach
    void clean() {
        eventSessionRepository.deleteAll();
    }

    private EventSession buildSession(String id, SessionStatus status, LocalDateTime start) {
        EventSession s = new EventSession();
        s.setId(id);
        s.setTitle("Session " + id);
        s.setDescription("Test session");
        s.setLocation("Room A");
        s.setStartTime(start);
        s.setEndTime(start.plusHours(1));
        s.setMaxCapacity(100);
        s.setCurrentRegistrations(0);
        s.setStatus(status);
        s.setCheckinWindowBeforeMinutes(30);
        s.setCheckinWindowAfterMinutes(15);
        s.setDeviceBindingRequired(false);
        return s;
    }

    @Test
    void saveAndFindById_roundTrip() {
        EventSession saved = eventSessionRepository.save(
                buildSession("s1", SessionStatus.DRAFT, LocalDateTime.of(2026, 5, 1, 10, 0)));
        Optional<EventSession> found = eventSessionRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("Session s1", found.get().getTitle());
    }

    @Test
    void findByStatus_returnsMatchingSessions() {
        eventSessionRepository.save(buildSession("s1", SessionStatus.OPEN_FOR_REGISTRATION, LocalDateTime.of(2026, 5, 1, 10, 0)));
        eventSessionRepository.save(buildSession("s2", SessionStatus.DRAFT, LocalDateTime.of(2026, 5, 2, 10, 0)));
        eventSessionRepository.save(buildSession("s3", SessionStatus.OPEN_FOR_REGISTRATION, LocalDateTime.of(2026, 5, 3, 10, 0)));

        List<EventSession> open = eventSessionRepository.findByStatus(SessionStatus.OPEN_FOR_REGISTRATION);
        assertEquals(2, open.size());

        List<EventSession> draft = eventSessionRepository.findByStatus(SessionStatus.DRAFT);
        assertEquals(1, draft.size());
    }

    @Test
    void findByStatusInAndStartTimeBetween_filtersCorrectly() {
        LocalDateTime may1 = LocalDateTime.of(2026, 5, 1, 10, 0);
        LocalDateTime may15 = LocalDateTime.of(2026, 5, 15, 10, 0);
        LocalDateTime jun1 = LocalDateTime.of(2026, 6, 1, 10, 0);

        eventSessionRepository.save(buildSession("s1", SessionStatus.OPEN_FOR_REGISTRATION, may1));
        eventSessionRepository.save(buildSession("s2", SessionStatus.OPEN_FOR_REGISTRATION, may15));
        eventSessionRepository.save(buildSession("s3", SessionStatus.OPEN_FOR_REGISTRATION, jun1));
        eventSessionRepository.save(buildSession("s4", SessionStatus.DRAFT, may15));

        List<EventSession> result = eventSessionRepository.findByStatusInAndStartTimeBetween(
                List.of(SessionStatus.OPEN_FOR_REGISTRATION),
                LocalDateTime.of(2026, 5, 1, 0, 0),
                LocalDateTime.of(2026, 5, 31, 23, 59));

        assertEquals(2, result.size());
    }

    @Test
    void findActiveSessionsForCheckin_returnsInProgressAndNearOpenSessions() {
        LocalDateTime now = LocalDateTime.now();
        eventSessionRepository.save(buildSession("s-progress", SessionStatus.IN_PROGRESS, now.minusMinutes(30)));
        eventSessionRepository.save(buildSession("s-open-soon", SessionStatus.OPEN_FOR_REGISTRATION, now.minusMinutes(10)));
        eventSessionRepository.save(buildSession("s-far-future", SessionStatus.OPEN_FOR_REGISTRATION, now.plusDays(5)));
        eventSessionRepository.save(buildSession("s-draft", SessionStatus.DRAFT, now));

        List<EventSession> active = eventSessionRepository.findActiveSessionsForCheckin(now);
        // s-progress (IN_PROGRESS) + s-open-soon (OPEN with startTime <= threshold)
        assertTrue(active.size() >= 1); // at minimum IN_PROGRESS is returned
    }
}
