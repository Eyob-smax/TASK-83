package com.eventops;

import com.eventops.domain.user.RoleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ImportCircuitBreakerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listSources_staffCanAccess() throws Exception {
        mockMvc.perform(get("/api/imports/sources")
                        .with(TestSecurity.user("staff-1", "staff", RoleType.EVENT_STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void listSources_attendeeForbidden() throws Exception {
        mockMvc.perform(get("/api/imports/sources")
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void circuitBreakerStatus_staffForbidden() throws Exception {
        mockMvc.perform(get("/api/imports/circuit-breaker")
                        .with(TestSecurity.user("staff-1", "staff", RoleType.EVENT_STAFF)))
                .andExpect(status().isForbidden());
    }

    @Test
    void circuitBreakerStatus_adminCanAccess() throws Exception {
        mockMvc.perform(get("/api/imports/circuit-breaker")
                        .with(TestSecurity.user("admin-1", "admin", RoleType.SYSTEM_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
