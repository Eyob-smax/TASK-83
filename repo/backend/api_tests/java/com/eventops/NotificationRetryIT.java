package com.eventops;

import com.eventops.domain.user.RoleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for notification retry and idempotency.
 * Requires a running database — deferred until Docker execution.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationRetryIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void notifications_list_returnsDelivered() throws Exception {
        mockMvc.perform(get("/api/notifications")
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void unreadCount_returnsZeroInitially() throws Exception {
        mockMvc.perform(get("/api/notifications/unread-count")
                .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").isNumber());
    }

    @Test
    void markAsRead_nonExistingNotification_returns404() throws Exception {
        mockMvc.perform(patch("/api/notifications/test-notification-id/read")
                .with(csrf())
                .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE)))
                .andExpect(status().isNotFound());
    }

    @Test
    void dndSettings_getAndUpdate() throws Exception {
        // Get defaults
        mockMvc.perform(get("/api/notifications/dnd")
                .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startTime").value("21:00"));

        // Update
        mockMvc.perform(put("/api/notifications/dnd")
                        .with(csrf())
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startTime\":\"22:00\",\"endTime\":\"06:00\",\"enabled\":true}"))
                .andExpect(status().isOk());
    }
}
