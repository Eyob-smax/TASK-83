package com.eventops.security.signature;

import com.eventops.security.auth.AuthController;
import com.eventops.service.admin.SecuritySettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignatureVerificationFilterTest {

    @Mock
    private SecuritySettingsService securitySettingsService;

    @Mock
    private FilterChain filterChain;

    private SignatureVerificationFilter filter;

    @BeforeEach
    void setUp() {
        SignatureProperties properties = new SignatureProperties();
        properties.setEnabled(true);
        properties.setSecretKey("fallback-secret-key");
        filter = new SignatureVerificationFilter(
                properties,
                securitySettingsService,
                new ObjectMapper().findAndRegisterModules()
        );

        when(securitySettingsService.isSignatureEnabled()).thenReturn(true);
    }

    @Test
    void shouldNotFilter_returnsTrueForUnauthenticatedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected/resource");

        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void shouldNotFilter_returnsFalseForAuthenticatedGetRequest() {
        MockHttpServletRequest request = authenticatedRequest("GET", "/api/auth/me", "session-secret");

        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    void shouldNotFilter_returnsTrueWhenSignatureDisabled() {
        when(securitySettingsService.isSignatureEnabled()).thenReturn(false);

        MockHttpServletRequest request = authenticatedRequest("POST", "/api/protected/resource", "session-secret");

        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void doFilter_rejectsAuthenticatedGetWithoutSignatureHeaders() throws Exception {
        MockHttpServletRequest request = authenticatedRequest("GET", "/api/auth/me", "session-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
    }

    @Test
    void doFilter_allowsAuthenticatedGetWithValidSignatureHeaders() throws Exception {
        when(securitySettingsService.getSignatureAlgorithm()).thenReturn("HmacSHA256");
        when(securitySettingsService.getSignatureMaxAgeSeconds()).thenReturn(300);
        String secret = "session-secret";
        MockHttpServletRequest request = authenticatedRequest("GET", "/api/auth/me", secret);
        String timestamp = Instant.now().toString();
        String nonce = "nonce-123";

        request.addHeader("X-Request-Timestamp", timestamp);
        request.addHeader("X-Request-Nonce", nonce);
        request.addHeader("X-Request-Signature", computeSignature(timestamp + nonce, secret));

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(response));
        assertEquals(200, response.getStatus());
    }

    @Test
    void doFilter_rejectsStaleAuthenticatedRequest() throws Exception {
        when(securitySettingsService.getSignatureMaxAgeSeconds()).thenReturn(300);
        String secret = "session-secret";
        MockHttpServletRequest request = authenticatedRequest("GET", "/api/auth/me", secret);
        String timestamp = Instant.now().minusSeconds(301).toString();
        String nonce = "nonce-124";

        request.addHeader("X-Request-Timestamp", timestamp);
        request.addHeader("X-Request-Nonce", nonce);
        request.addHeader("X-Request-Signature", computeSignature(timestamp + nonce, secret));

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
    }

    @Test
    void doFilter_allowsOptionsPreflightWithoutSignatureHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/protected/resource");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void doFilter_skipsLoginEndpointWithoutSignatureHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    private MockHttpServletRequest authenticatedRequest(String method, String uri, String sessionSecret) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthController.SESSION_SIGNATURE_TOKEN_KEY, sessionSecret);
        request.setSession(session);
        return request;
    }

    private String computeSignature(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
