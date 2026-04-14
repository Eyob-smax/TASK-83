package com.eventops;

import com.eventops.domain.user.AccountStatus;
import com.eventops.domain.user.RoleType;
import com.eventops.domain.user.User;
import com.eventops.repository.user.UserRepository;
import com.eventops.security.auth.AuthController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests verifying that the {@code SignatureVerificationFilter} enforces
 * HMAC signatures for authenticated requests when the feature is enabled.
 *
 * <p>These tests override the test profile's {@code signature.enabled=false} to
 * exercise the real signature pipeline end-to-end.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "eventops.security.signature.enabled=true",
        "eventops.security.signature.secret-key=integration-test-secret"
})
class SignatureFlowIT {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String SECRET = "integration-test-secret";
    private static final String HEADER_SIGNATURE = "X-Request-Signature";
    private static final String HEADER_TIMESTAMP = "X-Request-Timestamp";
    private static final String HEADER_NONCE = "X-Request-Nonce";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void seedTestUser() {
        User user = new User();
        user.setId("user-1");
        user.setUsername("user");
        user.setDisplayName("user");
        user.setPasswordHash("password-hash");
        user.setRoleType(RoleType.ATTENDEE);
        user.setStatus(AccountStatus.ACTIVE);
        userRepository.save(user);
    }

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    // ---------------------------------------------------------------
    // Happy path
    // ---------------------------------------------------------------

    @Test
    void authenticatedRequest_withValidSignature_passes() throws Exception {
        MockHttpSession session = sessionWithSecret(SECRET);
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String signature = sign(SECRET, timestamp, nonce, "");

        mockMvc.perform(get("/api/auth/me")
                        .session(session)
                        .with(TestSecurity.user("user-1", "user", RoleType.ATTENDEE))
                        .header(HEADER_SIGNATURE, signature)
                        .header(HEADER_TIMESTAMP, timestamp)
                        .header(HEADER_NONCE, nonce))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------
    // Missing headers
    // ---------------------------------------------------------------

    @Test
    void authenticatedRequest_withoutSignatureHeaders_returns401() throws Exception {
        MockHttpSession session = sessionWithSecret(SECRET);

        mockMvc.perform(get("/api/auth/me")
                        .session(session)
                        .with(TestSecurity.user("user-1", "user", RoleType.ATTENDEE)))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------
    // Expired timestamp
    // ---------------------------------------------------------------

    @Test
    void authenticatedRequest_withExpiredTimestamp_returns401() throws Exception {
        MockHttpSession session = sessionWithSecret(SECRET);
        // Timestamp more than 300 seconds old
        String timestamp = String.valueOf(Instant.now().minusSeconds(400).getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String signature = sign(SECRET, timestamp, nonce, "");

        mockMvc.perform(get("/api/auth/me")
                        .session(session)
                        .with(TestSecurity.user("user-1", "user", RoleType.ATTENDEE))
                        .header(HEADER_SIGNATURE, signature)
                        .header(HEADER_TIMESTAMP, timestamp)
                        .header(HEADER_NONCE, nonce))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------
    // Nonce replay
    // ---------------------------------------------------------------

    @Test
    void authenticatedRequest_withReplayedNonce_returns401() throws Exception {
        MockHttpSession session = sessionWithSecret(SECRET);
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String signature = sign(SECRET, timestamp, nonce, "");

        // First request succeeds
        mockMvc.perform(get("/api/auth/me")
                        .session(session)
                        .with(TestSecurity.user("user-1", "user", RoleType.ATTENDEE))
                        .header(HEADER_SIGNATURE, signature)
                        .header(HEADER_TIMESTAMP, timestamp)
                        .header(HEADER_NONCE, nonce))
                .andExpect(status().isOk());

        // Identical nonce replayed — must be rejected
        mockMvc.perform(get("/api/auth/me")
                        .session(session)
                        .with(TestSecurity.user("user-1", "user", RoleType.ATTENDEE))
                        .header(HEADER_SIGNATURE, signature)
                        .header(HEADER_TIMESTAMP, timestamp)
                        .header(HEADER_NONCE, nonce))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private static MockHttpSession sessionWithSecret(String secret) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthController.SESSION_SIGNATURE_TOKEN_KEY, secret);
        return session;
    }

    private static String sign(String secret, String timestamp, String nonce, String body)
            throws Exception {
        String payload = timestamp + nonce + body;
        Mac mac = Mac.getInstance(ALGORITHM);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
}
