package com.eventops;

import com.eventops.domain.user.RoleType;
import com.eventops.domain.event.EventSession;
import com.eventops.domain.event.SessionStatus;
import com.eventops.domain.registration.Registration;
import com.eventops.domain.registration.RegistrationStatus;
import com.eventops.repository.event.EventSessionRepository;
import com.eventops.repository.registration.RegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for registration behavior with seeded data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistrationFlowIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventSessionRepository eventSessionRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @BeforeEach
    void resetData() {
        registrationRepository.deleteAll();
        eventSessionRepository.deleteAll();
    }

    @Test
    void browseEvents_returnsSessionList() throws Exception {
        EventSession openSession = createSession("session-open", SessionStatus.OPEN_FOR_REGISTRATION, 2, 0);
        eventSessionRepository.save(openSession);

        mockMvc.perform(get("/api/events")
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content").isArray())
            .andExpect(jsonPath("$.data.content[0].id").value("session-open"));
    }

    @Test
    void register_unknownSession_returns404() throws Exception {
        mockMvc.perform(post("/api/registrations")
                        .with(csrf())
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"test-session-id\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].code").value("NOT_FOUND"));
    }

    @Test
    void register_duplicateAttemptOnUnknownSession_stillReturns404() throws Exception {
        EventSession openSession = createSession("session-dup", SessionStatus.OPEN_FOR_REGISTRATION, 5, 1);
        eventSessionRepository.save(openSession);

        Registration existing = new Registration();
        existing.setUserId("attendee-1");
        existing.setSessionId(openSession.getId());
        existing.setStatus(RegistrationStatus.CONFIRMED);
        registrationRepository.save(existing);

        mockMvc.perform(post("/api/registrations")
                        .with(csrf())
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE))
                        .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"session-dup\"}"))
            .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errors[0].code").value("DUPLICATE_REGISTRATION"));
    }

    @Test
    void register_fullSessionPlaceholder_returns404WithoutFixtureData() throws Exception {
        EventSession fullSession = createSession("full-session-id", SessionStatus.FULL, 1, 1);
        eventSessionRepository.save(fullSession);

        Registration existing = new Registration();
        existing.setUserId("attendee-existing");
        existing.setSessionId(fullSession.getId());
        existing.setStatus(RegistrationStatus.CONFIRMED);
        registrationRepository.save(existing);

        mockMvc.perform(post("/api/registrations")
                        .with(csrf())
                        .with(TestSecurity.user("attendee-2", "attendee2", RoleType.ATTENDEE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"full-session-id\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("WAITLISTED"))
            .andExpect(jsonPath("$.data.waitlistPosition").value(1));
    }

        private EventSession createSession(String id,
                           SessionStatus status,
                           int maxCapacity,
                           int currentRegistrations) {
        LocalDateTime start = LocalDateTime.now().plusHours(2);
        EventSession session = new EventSession();
        session.setId(id);
        session.setTitle("Session " + id);
        session.setDescription("Seeded integration test session");
        session.setLocation("Room A");
        session.setStartTime(start);
        session.setEndTime(start.plusHours(1));
        session.setStatus(status);
        session.setMaxCapacity(maxCapacity);
        session.setCurrentRegistrations(currentRegistrations);
        session.setCheckinWindowBeforeMinutes(30);
        session.setCheckinWindowAfterMinutes(15);
        session.setDeviceBindingRequired(false);
        return session;
        }
}
