package com.eventops;

import com.eventops.domain.user.RoleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FinanceFlowIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void financeManager_canAccessFinanceAccounts() throws Exception {
        mockMvc.perform(get("/api/finance/accounts")
                        .with(TestSecurity.user("finance-1", "finance", RoleType.FINANCE_MANAGER)))
                .andExpect(status().isOk());
    }

    @Test
    void admin_canAccessFinanceAccounts() throws Exception {
        mockMvc.perform(get("/api/finance/accounts")
                        .with(TestSecurity.user("admin-1", "admin", RoleType.SYSTEM_ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void attendee_cannotAccessFinanceAccounts() throws Exception {
        mockMvc.perform(get("/api/finance/accounts")
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE)))
                .andExpect(status().isForbidden());
    }
}
