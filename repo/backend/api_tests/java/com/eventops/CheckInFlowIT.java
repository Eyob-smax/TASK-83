package com.eventops;

import com.eventops.domain.checkin.PasscodeState;
import com.eventops.domain.event.EventSession;
import com.eventops.domain.event.SessionStatus;
import com.eventops.domain.registration.Registration;
import com.eventops.domain.registration.RegistrationStatus;
import com.eventops.domain.user.RoleType;
import com.eventops.repository.checkin.CheckInRecordRepository;
import com.eventops.repository.checkin.PasscodeStateRepository;
import com.eventops.repository.event.EventSessionRepository;
import com.eventops.repository.registration.RegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CheckInFlowIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventSessionRepository eventSessionRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private PasscodeStateRepository passcodeStateRepository;

    @Autowired
    private CheckInRecordRepository checkInRecordRepository;

    @BeforeEach
    void resetData() {
        checkInRecordRepository.deleteAll();
        registrationRepository.deleteAll();
        passcodeStateRepository.deleteAll();
        eventSessionRepository.deleteAll();
    }

    @Test
    void staff_canReachPasscodeEndpoint() throws Exception {
        seedPasscode("test-session", "123456");

        mockMvc.perform(get("/api/checkin/sessions/test-session/passcode")
                        .with(TestSecurity.user("staff-1", "staff", RoleType.EVENT_STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.passcode").value("123456"));
    }

    @Test
    void admin_canReachPasscodeEndpoint() throws Exception {
        seedPasscode("test-session", "654321");

        mockMvc.perform(get("/api/checkin/sessions/test-session/passcode")
                        .with(TestSecurity.user("admin-1", "admin", RoleType.SYSTEM_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.passcode").value("654321"));
    }

    @Test
    void attendee_cannotReachPasscodeEndpoint() throws Exception {
        mockMvc.perform(get("/api/checkin/sessions/test-session/passcode")
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void checkIn_ineligibleStatus_returns422() throws Exception {
        EventSession session = createSession("ineligible-session", LocalDateTime.now().plusMinutes(5));
        eventSessionRepository.save(session);

        Registration registration = new Registration();
        registration.setUserId("attendee-1");
        registration.setSessionId(session.getId());
        registration.setStatus(RegistrationStatus.WAITLISTED);
        registrationRepository.save(registration);

        seedPasscode(session.getId(), "222222");

        mockMvc.perform(post("/api/checkin/sessions/" + session.getId())
                        .with(csrf())
                        .with(TestSecurity.user("staff-1", "staff", RoleType.EVENT_STAFF))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"attendee-1\",\"passcode\":\"222222\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].code").value("INELIGIBLE_STATUS"));
    }

    @Test
    void checkIn_windowClosed_returns422ForStaff() throws Exception {
        EventSession session = createSession("window-closed-session", LocalDateTime.now().plusHours(3));
        eventSessionRepository.save(session);

        Registration registration = new Registration();
        registration.setUserId("attendee-1");
        registration.setSessionId(session.getId());
        registration.setStatus(RegistrationStatus.CONFIRMED);
        registrationRepository.save(registration);

        seedPasscode(session.getId(), "111111");

        mockMvc.perform(post("/api/checkin/sessions/" + session.getId())
                        .with(csrf())
                        .with(TestSecurity.user("staff-1", "staff", RoleType.EVENT_STAFF))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"attendee-1\",\"passcode\":\"111111\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].code").value("WINDOW_CLOSED"));
    }

    private void seedPasscode(String sessionId, String passcode) {
        PasscodeState passcodeState = new PasscodeState();
        passcodeState.setSessionId(sessionId);
        passcodeState.setCurrentPasscode(passcode);
        passcodeState.setGeneratedAt(Instant.now());
        passcodeState.setExpiresAt(Instant.now().plusSeconds(60));
        passcodeStateRepository.save(passcodeState);
    }

    private EventSession createSession(String id, LocalDateTime startTime) {
        EventSession session = new EventSession();
        session.setId(id);
        session.setTitle("Session " + id);
        session.setDescription("Check-in integration test session");
        session.setLocation("Room B");
        session.setStartTime(startTime);
        session.setEndTime(startTime.plusHours(1));
        session.setMaxCapacity(10);
        session.setCurrentRegistrations(1);
        session.setStatus(SessionStatus.OPEN_FOR_REGISTRATION);
        session.setCheckinWindowBeforeMinutes(30);
        session.setCheckinWindowAfterMinutes(15);
        session.setDeviceBindingRequired(false);
        return session;
    }
}
