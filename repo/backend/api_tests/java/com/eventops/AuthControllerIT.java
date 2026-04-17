package com.eventops;

import com.eventops.domain.user.AccountStatus;
import com.eventops.domain.user.RoleType;
import com.eventops.domain.user.User;
import com.eventops.repository.user.UserRepository;
import com.eventops.security.auth.AuthController;
import com.eventops.security.auth.PasswordService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordService passwordService;

    @BeforeEach
    void resetData() {
        userRepository.deleteAll();
    }

    @Test
    void login_success_returnsUserAndSignatureHeader() throws Exception {
        seedUser("auth-1", "api.user", RoleType.ATTENDEE);

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "api.user",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().exists(AuthController.SESSION_SIGNATURE_TOKEN_HEADER))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.data.username").value("api.user"));
    }

    @Test
    void login_badCredentials_returns401() throws Exception {
        seedUser("auth-1", "api.user", RoleType.ATTENDEE);

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "api.user",
                                  "password": "wrongpass"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].code").value("BAD_CREDENTIALS"));
    }

    @Test
    void login_unknownUser_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "no.such.user",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].code").value("BAD_CREDENTIALS"));
    }

    @Test
    void register_success_returnsCreatedUser() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "new.user",
                                  "password": "password123",
                                  "displayName": "New User"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Registration successful"))
                .andExpect(jsonPath("$.data.username").value("new.user"));
    }

    @Test
    void register_duplicateUsername_returns409() throws Exception {
        seedUser("existing-1", "dup.user", RoleType.ATTENDEE);

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "dup.user",
                                  "password": "password123",
                                  "displayName": "Dup User"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].code").value("USERNAME_TAKEN"));
    }

    @Test
    void me_withAuthenticatedUser_returnsCurrentUser() throws Exception {
        seedUser("auth-1", "api.user", RoleType.ATTENDEE);

        mockMvc.perform(get("/api/auth/me")
                        .with(TestSecurity.user("auth-1", "api.user", RoleType.ATTENDEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("api.user"));
    }

    @Test
    void me_withoutAuthenticatedUser_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_withAuthenticatedUser_returnsRefreshedSession() throws Exception {
        seedUser("auth-1", "api.user", RoleType.ATTENDEE);

        mockMvc.perform(post("/api/auth/refresh")
                        .with(csrf())
                        .with(TestSecurity.user("auth-1", "api.user", RoleType.ATTENDEE)))
                .andExpect(status().isOk())
                .andExpect(header().exists(AuthController.SESSION_SIGNATURE_TOKEN_HEADER))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Session refreshed"));
    }

    @Test
    void refresh_withoutAuthenticatedUser_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_returnsSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .with(csrf())
                        .with(TestSecurity.user("auth-1", "api.user", RoleType.ATTENDEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Logout successful"));
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
}
