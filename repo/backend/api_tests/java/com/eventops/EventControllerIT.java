package com.eventops;

import com.eventops.domain.event.EventSession;
import com.eventops.domain.event.SessionStatus;
import com.eventops.domain.user.RoleType;
import com.eventops.repository.event.EventSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private EventSessionRepository eventSessionRepository;

    @BeforeEach
    void resetData() {
        eventSessionRepository.deleteAll();
    }

    @Test
    void listSessions_asAuthenticatedUser_returnsSessionPage() throws Exception {
        EventSession session = createSession("session-1", "Keynote Session",
                SessionStatus.OPEN_FOR_REGISTRATION, 100, 88);
        eventSessionRepository.save(session);

        mockMvc.perform(get("/api/events")
                        .param("status", "OPEN_FOR_REGISTRATION")
                        .param("search", "Keynote")
                        .param("page", "0")
                        .param("size", "20")
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value("session-1"))
                .andExpect(jsonPath("$.data.content[0].title").value("Keynote Session"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void getSession_asAuthenticatedUser_returnsSession() throws Exception {
        EventSession session = createSession("session-2", "Keynote Session",
                SessionStatus.OPEN_FOR_REGISTRATION, 100, 92);
        eventSessionRepository.save(session);

        mockMvc.perform(get("/api/events/session-2")
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("session-2"))
                .andExpect(jsonPath("$.data.location").value("Main Hall"));
    }

    @Test
    void getAvailability_asAuthenticatedUser_returnsRemainingSeats() throws Exception {
        EventSession session = createSession("session-3", "Keynote Session",
                SessionStatus.OPEN_FOR_REGISTRATION, 100, 97);
        eventSessionRepository.save(session);

        mockMvc.perform(get("/api/events/session-3/availability")
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("session-3"))
                .andExpect(jsonPath("$.data.remainingSeats").value(3))
                .andExpect(jsonPath("$.data.status").value("OPEN_FOR_REGISTRATION"));
    }

    private EventSession createSession(String id, String title, SessionStatus status,
                                       int maxCapacity, int currentRegistrations) {
        LocalDateTime start = LocalDateTime.of(2026, 4, 20, 9, 0);
        EventSession session = new EventSession();
        session.setId(id);
        session.setTitle(title);
        session.setDescription("Opening keynote");
        session.setLocation("Main Hall");
        session.setStartTime(start);
        session.setEndTime(start.plusHours(1));
        session.setMaxCapacity(maxCapacity);
        session.setCurrentRegistrations(currentRegistrations);
        session.setStatus(status);
        session.setCheckinWindowBeforeMinutes(30);
        session.setCheckinWindowAfterMinutes(15);
        session.setDeviceBindingRequired(false);
        return session;
    }
}
