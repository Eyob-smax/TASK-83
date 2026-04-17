package com.eventops;

import com.eventops.domain.admin.SecuritySettings;
import com.eventops.domain.user.AccountStatus;
import com.eventops.domain.user.RoleType;
import com.eventops.domain.user.User;
import com.eventops.repository.admin.SecuritySettingsRepository;
import com.eventops.repository.user.UserRepository;
import com.eventops.security.auth.PasswordService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private SecuritySettingsRepository securitySettingsRepository;
    @Autowired private PasswordService passwordService;

    @BeforeEach
    void resetData() {
        userRepository.deleteAll();
        securitySettingsRepository.deleteAll();
    }

    @AfterEach
    void clearSecuritySettings() {
        securitySettingsRepository.deleteAll();
    }

    @Test
    void listUsers_asSystemAdmin_returnsPagedUsers() throws Exception {
        seedUser("user-1", "staff.user", RoleType.EVENT_STAFF);

        mockMvc.perform(get("/api/admin/users")
                        .param("page", "0")
                        .param("size", "20")
                        .with(TestSecurity.user("admin-1", "admin", RoleType.SYSTEM_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].username").value("staff.user"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void createUser_asSystemAdmin_returnsCreatedUser() throws Exception {
        mockMvc.perform(post("/api/admin/users")
                        .with(csrf())
                        .with(TestSecurity.user("admin-1", "admin", RoleType.SYSTEM_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "new.user",
                                  "password": "password123",
                                  "displayName": "New User",
                                  "roleType": "EVENT_STAFF",
                                  "contactInfo": "new.user@example.com"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("new.user"));
    }

    @Test
    void updateUser_asSystemAdmin_returnsUpdatedUser() throws Exception {
        User user = seedUser("user-3", "target.user", RoleType.ATTENDEE);

        mockMvc.perform(put("/api/admin/users/" + user.getId())
                        .with(csrf())
                        .with(TestSecurity.user("admin-1", "admin", RoleType.SYSTEM_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Updated User",
                                  "roleType": "FINANCE_MANAGER"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roleType").value("FINANCE_MANAGER"));
    }

    @Test
    void getSecuritySettings_asSystemAdmin_returnsSettings() throws Exception {
        seedSecuritySettings();

        mockMvc.perform(get("/api/admin/security/settings")
                        .with(TestSecurity.user("admin-1", "admin", RoleType.SYSTEM_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.rateLimitPerMinute").exists());
    }

    @Test
    void updateSecuritySettings_asSystemAdmin_returnsUpdatedSettings() throws Exception {
        seedSecuritySettings();

        mockMvc.perform(put("/api/admin/security/settings")
                        .with(csrf())
                        .with(TestSecurity.user("admin-1", "admin", RoleType.SYSTEM_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rateLimitPerMinute": 120,
                                  "loginMaxAttempts": 5,
                                  "loginLockoutMinutes": 15,
                                  "signatureEnabled": true,
                                  "signatureAlgorithm": "HmacSHA256",
                                  "signatureMaxAgeSeconds": 300,
                                  "piiDisplayMode": "MASKED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.rateLimitPerMinute").value(120));
    }

    private User seedUser(String id, String username, RoleType role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash(passwordService.encode("password123"));
        user.setDisplayName("Display " + username);
        user.setRoleType(role);
        user.setStatus(AccountStatus.ACTIVE);
        return userRepository.save(user);
    }

    private void seedSecuritySettings() {
        SecuritySettings settings = new SecuritySettings();
        settings.setId(SecuritySettings.DEFAULT_ID);
        settings.setRateLimitPerMinute(60);
        settings.setLoginMaxAttempts(5);
        settings.setLoginLockoutMinutes(15);
        settings.setSignatureEnabled(false);
        settings.setSignatureAlgorithm("HmacSHA256");
        settings.setSignatureMaxAgeSeconds(300);
        settings.setPiiDisplayMode("MASKED");
        securitySettingsRepository.save(settings);
    }
}
