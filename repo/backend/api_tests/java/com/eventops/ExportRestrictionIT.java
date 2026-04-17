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
class ExportRestrictionIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exportPolicies_requiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/exports/policies")
                        .with(TestSecurity.user("staff-1", "staff", RoleType.EVENT_STAFF)))
                .andExpect(status().isForbidden());
    }

    @Test
    void exportPolicies_allowsAdmin() throws Exception {
        mockMvc.perform(get("/api/exports/policies")
                        .with(TestSecurity.user("admin-1", "admin", RoleType.SYSTEM_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void downloadExport_requiresAuthenticatedRole() throws Exception {
        mockMvc.perform(get("/api/exports/nonexistent/download")
                        .with(TestSecurity.user("staff-1", "staff", RoleType.EVENT_STAFF)))
                .andExpect(status().isNotFound());
    }

    @Test
    void auditExport_download_requiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/audit/logs")
                        .with(TestSecurity.user("finance-1", "finance", RoleType.FINANCE_MANAGER)))
                .andExpect(status().isForbidden());
    }
}
