package com.eventops;

import com.eventops.domain.event.EventSession;
import com.eventops.domain.event.SessionStatus;
import com.eventops.domain.notification.DndRule;
import com.eventops.domain.notification.NotificationType;
import com.eventops.domain.notification.SendLog;
import com.eventops.domain.notification.SendStatus;
import com.eventops.domain.notification.Subscription;
import com.eventops.domain.registration.Registration;
import com.eventops.domain.registration.RegistrationStatus;
import com.eventops.domain.user.RoleType;
import com.eventops.repository.event.EventSessionRepository;
import com.eventops.repository.notification.DndRuleRepository;
import com.eventops.repository.notification.SendLogRepository;
import com.eventops.repository.notification.SubscriptionRepository;
import com.eventops.repository.registration.RegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistrationNotificationControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private EventSessionRepository eventSessionRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private SendLogRepository sendLogRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private DndRuleRepository dndRuleRepository;

    @BeforeEach
    void resetData() {
        sendLogRepository.deleteAll();
        subscriptionRepository.deleteAll();
        dndRuleRepository.deleteAll();
        registrationRepository.deleteAll();
        eventSessionRepository.deleteAll();
    }

    // ---- Registration Tests ----

    @Test
    void register_confirmed_returnsConfirmationMessage() throws Exception {
        EventSession session = createSession("session-1", SessionStatus.OPEN_FOR_REGISTRATION, 10, 0);
        eventSessionRepository.save(session);

        mockMvc.perform(post("/api/registrations")
                        .with(csrf())
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"session-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Registration confirmed"));
    }

    @Test
    void register_waitlisted_returnsWaitlistMessage() throws Exception {
        EventSession session = createSession("session-full", SessionStatus.FULL, 1, 1);
        eventSessionRepository.save(session);

        // Seed an existing confirmed registration so the session is truly full
        Registration existing = new Registration();
        existing.setUserId("existing-user");
        existing.setSessionId("session-full");
        existing.setStatus(RegistrationStatus.CONFIRMED);
        registrationRepository.save(existing);

        mockMvc.perform(post("/api/registrations")
                        .with(csrf())
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"session-full\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Added to waitlist at position 1"));
    }

    @Test
    void listRegistrations_returnsUserRegistrations() throws Exception {
        EventSession session = createSession("session-list", SessionStatus.OPEN_FOR_REGISTRATION, 10, 1);
        eventSessionRepository.save(session);

        Registration reg = new Registration();
        reg.setUserId("attendee-1");
        reg.setSessionId("session-list");
        reg.setStatus(RegistrationStatus.CONFIRMED);
        registrationRepository.save(reg);

        mockMvc.perform(get("/api/registrations")
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getRegistration_returnsSingleRegistration() throws Exception {
        EventSession session = createSession("session-get", SessionStatus.OPEN_FOR_REGISTRATION, 10, 1);
        eventSessionRepository.save(session);

        Registration reg = new Registration();
        reg.setUserId("attendee-1");
        reg.setSessionId("session-get");
        reg.setStatus(RegistrationStatus.CONFIRMED);
        Registration saved = registrationRepository.save(reg);

        mockMvc.perform(get("/api/registrations/" + saved.getId())
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void cancelRegistration_returnsCancelledRegistration() throws Exception {
        EventSession session = createSession("session-cancel", SessionStatus.OPEN_FOR_REGISTRATION, 10, 1);
        eventSessionRepository.save(session);

        Registration reg = new Registration();
        reg.setUserId("attendee-1");
        reg.setSessionId("session-cancel");
        reg.setStatus(RegistrationStatus.CONFIRMED);
        Registration saved = registrationRepository.save(reg);

        mockMvc.perform(delete("/api/registrations/" + saved.getId())
                        .with(csrf())
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Registration cancelled"));
    }

    @Test
    void waitlist_returnsWaitlistEntries() throws Exception {
        EventSession session = createSession("session-wl", SessionStatus.FULL, 1, 1);
        eventSessionRepository.save(session);

        Registration reg = new Registration();
        reg.setUserId("attendee-1");
        reg.setSessionId("session-wl");
        reg.setStatus(RegistrationStatus.WAITLISTED);
        reg.setWaitlistPosition(2);
        registrationRepository.save(reg);

        mockMvc.perform(get("/api/registrations/waitlist")
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void sessionRoster_returnsRosterEntries() throws Exception {
        EventSession session = createSession("session-roster", SessionStatus.OPEN_FOR_REGISTRATION, 10, 1);
        eventSessionRepository.save(session);

        Registration reg = new Registration();
        reg.setUserId("attendee-1");
        reg.setSessionId("session-roster");
        reg.setStatus(RegistrationStatus.CONFIRMED);
        registrationRepository.save(reg);

        mockMvc.perform(get("/api/registrations/session/session-roster")
                        .with(TestSecurity.user("staff-1", "staff", RoleType.EVENT_STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ---- Notification Tests ----

    @Test
    void listNotifications_returnsPage() throws Exception {
        seedDeliveredNotification("attendee-1", "notification-1");

        mockMvc.perform(get("/api/notifications")
                        .param("page", "0")
                        .param("size", "20")
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void unreadCount_returnsUnreadTotal() throws Exception {
        seedDeliveredNotification("attendee-1", "notif-1");
        seedDeliveredNotification("attendee-1", "notif-2");

        mockMvc.perform(get("/api/notifications/unread-count")
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.count").value(2));
    }

    @Test
    void markAsRead_returnsSuccessMessage() throws Exception {
        SendLog log = seedDeliveredNotification("attendee-1", "notif-read");

        mockMvc.perform(patch("/api/notifications/" + log.getId() + "/read")
                        .with(csrf())
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Notification marked as read"));
    }

    @Test
    void getSubscriptions_returnsSubscriptions() throws Exception {
        Subscription sub = new Subscription();
        sub.setUserId("attendee-1");
        sub.setNotificationType(NotificationType.SYSTEM_ALERT);
        sub.setEnabled(true);
        subscriptionRepository.save(sub);

        mockMvc.perform(get("/api/notifications/subscriptions")
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updateSubscriptions_returnsSuccessMessage() throws Exception {
        mockMvc.perform(put("/api/notifications/subscriptions")
                        .with(csrf())
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "notificationType": "SYSTEM_ALERT",
                                    "enabled": true
                                  }
                                ]
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Subscriptions updated"));
    }

    @Test
    void getDndSettings_returnsCurrentWindow() throws Exception {
        DndRule rule = new DndRule();
        rule.setUserId("attendee-1");
        rule.setStartTime(LocalTime.of(21, 0));
        rule.setEndTime(LocalTime.of(7, 0));
        rule.setEnabled(true);
        dndRuleRepository.save(rule);

        mockMvc.perform(get("/api/notifications/dnd")
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.startTime").value("21:00"))
                .andExpect(jsonPath("$.data.endTime").value("07:00"))
                .andExpect(jsonPath("$.data.enabled").value(true));
    }

    @Test
    void updateDndSettings_returnsUpdatedWindow() throws Exception {
        mockMvc.perform(put("/api/notifications/dnd")
                        .with(csrf())
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startTime": "22:00",
                                  "endTime": "06:00",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("DND settings updated"))
                .andExpect(jsonPath("$.data.startTime").value("22:00"))
                .andExpect(jsonPath("$.data.endTime").value("06:00"));
    }

    // ---- seed helpers ----

    private EventSession createSession(String id, SessionStatus status, int maxCapacity, int currentRegs) {
        LocalDateTime start = LocalDateTime.now().plusHours(2);
        EventSession session = new EventSession();
        session.setId(id);
        session.setTitle("Session " + id);
        session.setDescription("Integration test session");
        session.setLocation("Room A");
        session.setStartTime(start);
        session.setEndTime(start.plusHours(1));
        session.setStatus(status);
        session.setMaxCapacity(maxCapacity);
        session.setCurrentRegistrations(currentRegs);
        session.setCheckinWindowBeforeMinutes(30);
        session.setCheckinWindowAfterMinutes(15);
        session.setDeviceBindingRequired(false);
        return session;
    }

    private SendLog seedDeliveredNotification(String userId, String referenceId) {
        SendLog log = new SendLog();
        log.setUserId(userId);
        log.setNotificationType(NotificationType.SYSTEM_ALERT);
        log.setSubject("Test notification");
        log.setBody("Test notification body");
        log.setStatus(SendStatus.DELIVERED);
        log.setDeliveredAt(Instant.now());
        log.setIdempotencyKey("SYSTEM_ALERT:" + userId + ":" + referenceId);
        log.setReferenceId(referenceId);
        return sendLogRepository.save(log);
    }
}
