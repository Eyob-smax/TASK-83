package com.eventops;

import com.eventops.domain.event.EventSession;
import com.eventops.domain.event.SessionStatus;
import com.eventops.domain.user.AccountStatus;
import com.eventops.domain.user.RoleType;
import com.eventops.domain.user.User;
import com.eventops.repository.event.EventSessionRepository;
import com.eventops.repository.user.UserRepository;
import com.eventops.security.auth.PasswordService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * True no-mock HTTP integration tests using real TCP transport.
 *
 * <p>Uses {@code RANDOM_PORT} + Java {@link HttpClient} for real socket HTTP.
 * No MockMvc, no {@code @MockBean}, no Mockito. All services, repositories,
 * and authorization rules are production code.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "eventops.security.signature.enabled=true")
class RealHttpIT {

    @LocalServerPort private int port;
    @Autowired private UserRepository userRepository;
    @Autowired private EventSessionRepository eventSessionRepository;
    @Autowired private PasswordService passwordService;
    @Autowired private ObjectMapper objectMapper;
    private String baseUrl;
    private HttpClient httpClient;
    private Map<String, String> cookies;
    private String sessionSignatureToken;

    @BeforeEach
    void setup() {
        baseUrl = "http://localhost:" + port + "/api";
        eventSessionRepository.deleteAll();
        userRepository.deleteAll();
        cookies = new HashMap<>();
        sessionSignatureToken = null;
        httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    }

    // ── Auth ──
    @Test
    void register_returns201() throws Exception {
        assertEquals(201, post("/auth/register", Map.of(
                "username", "u_reg",
                "password", "password123",
                "displayName", "R")).statusCode());
    }

    @Test
    void login_returns200() throws Exception {
        seedUser("u_li", "u_li", RoleType.ATTENDEE);
        HttpResponse<String> response = post("/auth/login", Map.of("username", "u_li", "password", "password123"));
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"success\":true"));
        assertTrue(cookies.containsKey("JSESSIONID"));
    }

    @Test
    void login_bad_returns401() throws Exception {
        assertEquals(401, post("/auth/login", Map.of("username", "xxx", "password", "password123")).statusCode());
    }

    @Test
    void me_returns200() throws Exception {
        registerAndLogin("u_me1");
        HttpResponse<String> response = get("/auth/me");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("u_me1"));
    }

    @Test
    void me_noSession_unauthorized() throws Exception {
        int code = get("/auth/me").statusCode();
        assertTrue(code == 401 || code == 403);
    }

    @Test
    void refresh_returns200() throws Exception {
        registerAndLogin("u_rf1");
        assertEquals(200, post("/auth/refresh", Map.of()).statusCode());
    }

    @Test
    void logout_returns200() throws Exception {
        registerAndLogin("u_lo1");
        assertEquals(200, post("/auth/logout", Map.of()).statusCode());
    }

    // ── Events ──
    @Test
    void listEvents_returns200() throws Exception {
        registerAndLogin("u_ev1");
        assertEquals(200, get("/events").statusCode());
    }

    @Test
    void listEvents_noSession_unauthorized() throws Exception {
        int code = get("/events").statusCode();
        assertTrue(code == 401 || code == 403);
    }

    @Test
    void getEvent_returns200() throws Exception {
        registerAndLogin("u_evd");
        seedSession("sd");
        assertEquals(200, get("/events/sd").statusCode());
    }

    @Test
    void getAvailability_returns200() throws Exception {
        registerAndLogin("u_eva");
        seedSession("sa");
        assertEquals(200, get("/events/sa/availability").statusCode());
    }

    // ── Finance ──
    @Test
    void periods_asFin_200() throws Exception {
        seedAndLogin("u_fp", RoleType.FINANCE_MANAGER);
        assertEquals(200, get("/finance/periods").statusCode());
    }

    @Test
    void periods_asAtt_403() throws Exception {
        registerAndLogin("u_fa");
        assertEquals(403, get("/finance/periods").statusCode());
    }

    @Test
    void accounts_200() throws Exception {
        seedAndLogin("u_fac", RoleType.FINANCE_MANAGER);
        assertEquals(200, get("/finance/accounts").statusCode());
    }

    @Test
    void costCenters_200() throws Exception {
        seedAndLogin("u_fcc", RoleType.FINANCE_MANAGER);
        assertEquals(200, get("/finance/cost-centers").statusCode());
    }

    @Test
    void rules_200() throws Exception {
        seedAndLogin("u_fr", RoleType.FINANCE_MANAGER);
        assertEquals(200, get("/finance/rules").statusCode());
    }

    @Test
    void postings_200() throws Exception {
        seedAndLogin("u_fpo", RoleType.FINANCE_MANAGER);
        assertEquals(200, get("/finance/postings").statusCode());
    }

    // ── Admin ──
    @Test
    void users_asAdmin_200() throws Exception {
        seedAndLogin("u_au", RoleType.SYSTEM_ADMIN);
        assertEquals(200, get("/admin/users").statusCode());
    }

    @Test
    void users_asAtt_403() throws Exception {
        registerAndLogin("u_aa");
        assertEquals(403, get("/admin/users").statusCode());
    }

    @Test
    void secSettings_200() throws Exception {
        seedAndLogin("u_as", RoleType.SYSTEM_ADMIN);
        assertEquals(200, get("/admin/security/settings").statusCode());
    }

    @Test
    void backups_200() throws Exception {
        seedAndLogin("u_ab", RoleType.SYSTEM_ADMIN);
        assertEquals(200, get("/admin/backups").statusCode());
    }

    @Test
    void retention_200() throws Exception {
        seedAndLogin("u_ar", RoleType.SYSTEM_ADMIN);
        assertEquals(200, get("/admin/backups/retention").statusCode());
    }

    // ── Notifications ──
    @Test
    void notifications_200() throws Exception {
        registerAndLogin("u_n1");
        assertEquals(200, get("/notifications").statusCode());
    }

    @Test
    void unread_200() throws Exception {
        registerAndLogin("u_uc");
        assertEquals(200, get("/notifications/unread-count").statusCode());
    }

    @Test
    void markRead_404() throws Exception {
        registerAndLogin("u_mr");
        assertEquals(404, patch("/notifications/x/read").statusCode());
    }

    @Test
    void subs_200() throws Exception {
        registerAndLogin("u_su");
        assertEquals(200, get("/notifications/subscriptions").statusCode());
    }

    @Test
    void dnd_200() throws Exception {
        registerAndLogin("u_dn");
        assertEquals(200, get("/notifications/dnd").statusCode());
    }

    // ── Registrations ──
    @Test
    void regs_200() throws Exception {
        registerAndLogin("u_rl");
        assertEquals(200, get("/registrations").statusCode());
    }

    @Test
    void createReg_404() throws Exception {
        registerAndLogin("u_rc");
        int code = post("/registrations", Map.of("sessionId", "none")).statusCode();
        assertTrue(code == 400 || code == 404);
    }

    @Test
    void waitlist_200() throws Exception {
        registerAndLogin("u_wl");
        assertEquals(200, get("/registrations/waitlist").statusCode());
    }

    // ── Check-in ──
    @Test
    void passcode_asStaff() throws Exception {
        seedAndLogin("u_sp", RoleType.EVENT_STAFF);
        int code = get("/checkin/sessions/any/passcode").statusCode();
        assertTrue(code == 200 || code == 404);
    }

    // ── Imports ──
    @Test
    void importSources_200() throws Exception {
        seedAndLogin("u_is", RoleType.SYSTEM_ADMIN);
        assertEquals(200, get("/imports/sources").statusCode());
    }

    @Test
    void importJobs_200() throws Exception {
        seedAndLogin("u_ij", RoleType.SYSTEM_ADMIN);
        assertEquals(200, get("/imports/jobs").statusCode());
    }

    @Test
    void circuitBreaker_200() throws Exception {
        seedAndLogin("u_cb", RoleType.SYSTEM_ADMIN);
        assertEquals(200, get("/imports/circuit-breaker").statusCode());
    }

    // ── Exports ──
    @Test
    void exportPolicies_200() throws Exception {
        seedAndLogin("u_ep", RoleType.SYSTEM_ADMIN);
        assertEquals(200, get("/exports/policies").statusCode());
    }

    // ── Audit ──
    @Test
    void auditLogs_200() throws Exception {
        seedAndLogin("u_al", RoleType.SYSTEM_ADMIN);
        assertEquals(200, get("/audit/logs").statusCode());
    }

    // ── Attachments ──
    @Test
    void attachSession_201() throws Exception {
        registerAndLogin("u_at");
        int code = post("/attachments/sessions", Map.of(
            "fileName", "f.pdf",
            "totalSize", 1024,
            "totalChunks", 1)).statusCode();
        assertTrue(code == 201 || code == 400);
    }

    // ── Helpers ──
    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .GET();
        addCookieHeader(requestBuilder);
        addSignatureHeaders(requestBuilder, path, "");

        HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        captureCookies(response);
        return response;
    }

    private HttpResponse<String> post(String path, Object body) throws IOException, InterruptedException {
        ensureCsrfToken();
        String requestBody = objectMapper.writeValueAsString(body);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody));
        addCookieHeader(requestBuilder);
        addCsrfHeader(requestBuilder);
        addSignatureHeaders(requestBuilder, path, requestBody);

        HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        captureCookies(response);
        return response;
    }

    private HttpResponse<String> patch(String path) throws IOException, InterruptedException {
        ensureCsrfToken();
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(""));
        addCookieHeader(requestBuilder);
        addCsrfHeader(requestBuilder);
        addSignatureHeaders(requestBuilder, path, "");

        HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        captureCookies(response);
        return response;
    }

    private void ensureCsrfToken() throws IOException, InterruptedException {
        if (cookies.containsKey("XSRF-TOKEN")) {
            return;
        }

        // CookieCsrfTokenRepository accepts the token value from the request cookie.
        // Seed one here so raw HttpClient requests can satisfy CSRF checks without
        // relying on deferred token issuance side-effects.
        cookies.put("XSRF-TOKEN", UUID.randomUUID().toString());
    }

    private void addCookieHeader(HttpRequest.Builder requestBuilder) {
        if (cookies.isEmpty()) {
            return;
        }

        String cookieHeader = cookies.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "; " + right)
                .orElse("");
        if (!cookieHeader.isEmpty()) {
            requestBuilder.header("Cookie", cookieHeader);
        }
    }

    private void addCsrfHeader(HttpRequest.Builder requestBuilder) {
        String csrfToken = cookies.get("XSRF-TOKEN");
        if (csrfToken != null && !csrfToken.isEmpty()) {
            requestBuilder.header("X-XSRF-TOKEN", csrfToken);
        }
    }

    private void addSignatureHeaders(HttpRequest.Builder requestBuilder, String path, String body) {
        if (sessionSignatureToken == null || isUnsignedPath(path)) {
            return;
        }

        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String payload = timestamp + nonce + body;

        requestBuilder.header("X-Request-Timestamp", timestamp);
        requestBuilder.header("X-Request-Nonce", nonce);
        requestBuilder.header("X-Request-Signature", signPayload(payload, sessionSignatureToken));
    }

    private boolean isUnsignedPath(String path) {
        return "/auth/login".equals(path) || "/auth/register".equals(path);
    }

    private String signPayload(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign RealHttpIT request payload", e);
        }
    }

    private void captureCookies(HttpResponse<?> response) {
        for (String header : response.headers().allValues("set-cookie")) {
            String cookiePart = header.split(";", 2)[0];
            int separator = cookiePart.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String name = cookiePart.substring(0, separator);
            String value = cookiePart.substring(separator + 1);
            if (!value.isEmpty()) {
                cookies.put(name, value);
            }
        }

        response.headers()
                .firstValue("X-Session-Signature-Token")
                .ifPresent(token -> sessionSignatureToken = token);
    }

    private void registerAndLogin(String username) throws IOException, InterruptedException {
        post("/auth/register", Map.of(
                "username", username,
                "password", "password123",
                "displayName", "T " + username));
        post("/auth/login", Map.of("username", username, "password", "password123"));
    }

    private void seedAndLogin(String username, RoleType roleType) throws IOException, InterruptedException {
        seedUser(username, username, roleType);
        post("/auth/login", Map.of("username", username, "password", "password123"));
    }

    private void seedUser(String id, String username, RoleType roleType) {
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }

        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash(passwordService.encode("password123"));
        user.setDisplayName("T " + username);
        user.setRoleType(roleType);
        user.setStatus(AccountStatus.ACTIVE);
        userRepository.save(user);
    }

    private void seedSession(String id) {
        EventSession session = new EventSession();
        session.setId(id);
        session.setTitle("S");
        session.setDescription("d");
        session.setLocation("A");
        session.setStartTime(LocalDateTime.now().plusDays(7));
        session.setEndTime(LocalDateTime.now().plusDays(7).plusHours(1));
        session.setMaxCapacity(100);
        session.setCurrentRegistrations(0);
        session.setStatus(SessionStatus.OPEN_FOR_REGISTRATION);
        session.setCheckinWindowBeforeMinutes(30);
        session.setCheckinWindowAfterMinutes(15);
        session.setDeviceBindingRequired(false);
        eventSessionRepository.save(session);
    }
}
