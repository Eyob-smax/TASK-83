package com.eventops.security.signature;

import com.eventops.security.auth.AuthController;
import com.eventops.common.dto.ApiResponse;
import com.eventops.service.admin.SecuritySettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import jakarta.annotation.PostConstruct;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Servlet filter that verifies HMAC request signatures for authenticated API calls.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SignatureVerificationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SignatureVerificationFilter.class);

    private static final String HEADER_SIGNATURE = "X-Request-Signature";
    private static final String HEADER_TIMESTAMP = "X-Request-Timestamp";
    private static final String HEADER_NONCE = "X-Request-Nonce";

    private static final Set<String> SKIP_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register"
    );

    private final SignatureProperties properties;
    private final SecuritySettingsService securitySettingsService;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Long> nonceStore = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor;

    public SignatureVerificationFilter(SignatureProperties properties,
                                       SecuritySettingsService securitySettingsService,
                                       ObjectMapper objectMapper) {
        this.properties = properties;
        this.securitySettingsService = securitySettingsService;
        this.objectMapper = objectMapper;
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "nonce-cleanup");
            thread.setDaemon(true);
            return thread;
        });
        this.cleanupExecutor.scheduleAtFixedRate(this::purgeExpiredNonces, 60, 60, TimeUnit.SECONDS);

        log.info("Signature verification filter initialized");
    }

    @PostConstruct
    public void validateConfig() {
        if (properties.isEnabled()) {
            log.info("Signature verification enabled with algorithm={}", properties.getAlgorithm());
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!securitySettingsService.isSignatureEnabled()) {
            return true;
        }
        if (SKIP_PATHS.contains(request.getRequestURI())) {
            return true;
        }
        return request.getSession(false) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper wrappedRequest =
                (request instanceof ContentCachingRequestWrapper)
                        ? (ContentCachingRequestWrapper) request
                        : new ContentCachingRequestWrapper(request);

        String signature = wrappedRequest.getHeader(HEADER_SIGNATURE);
        String timestampHeader = wrappedRequest.getHeader(HEADER_TIMESTAMP);
        String nonce = wrappedRequest.getHeader(HEADER_NONCE);

        if (signature == null || timestampHeader == null || nonce == null) {
            log.warn("Missing signature headers on {} {}", request.getMethod(), request.getRequestURI());
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Missing required signature headers");
            return;
        }

        long timestamp;
        try {
            timestamp = parseTimestamp(timestampHeader);
        } catch (RuntimeException e) {
            log.warn("Invalid timestamp header format on {} {}", request.getMethod(), request.getRequestURI());
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid request timestamp");
            return;
        }

        int maxAgeSeconds = securitySettingsService.getSignatureMaxAgeSeconds();
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - timestamp) > maxAgeSeconds) {
            log.warn("Stale request timestamp on {} {} (age={}s)",
                    request.getMethod(), request.getRequestURI(), Math.abs(now - timestamp));
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Request timestamp is too old or too far in the future");
            return;
        }

        long nonceExpiry = System.currentTimeMillis() + (maxAgeSeconds * 1000L);
        Long existing = nonceStore.putIfAbsent(nonce, nonceExpiry);
        if (existing != null) {
            log.warn("Duplicate nonce detected on {} {}", request.getMethod(), request.getRequestURI());
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Duplicate request nonce - possible replay attack");
            return;
        }

        wrappedRequest.getInputStream().readAllBytes();
        byte[] body = wrappedRequest.getContentAsByteArray();

        String payload = timestampHeader + nonce + new String(body, StandardCharsets.UTF_8);
        String computedSignature;
        try {
            String signatureSecret = resolveSignatureSecret(request);
            computedSignature = computeHmac(payload, securitySettingsService.getSignatureAlgorithm(), signatureSecret);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to compute HMAC signature: {}", e.getMessage());
            writeErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Signature verification configuration error");
            return;
        }

        if (!MessageDigest.isEqual(
                computedSignature.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Signature mismatch on {} {}", request.getMethod(), request.getRequestURI());
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid request signature");
            return;
        }

        log.debug("Signature verified for {} {}", request.getMethod(), request.getRequestURI());
        filterChain.doFilter(wrappedRequest, response);
    }

    private String computeHmac(String payload, String algorithm, String secretKey)
            throws NoSuchAlgorithmException, InvalidKeyException {
        if (secretKey == null || secretKey.isBlank()) {
            throw new InvalidKeyException(
                    "Signature secret key is not configured for this request context.");
        }
        Mac mac = Mac.getInstance(algorithm);
        SecretKeySpec keySpec = new SecretKeySpec(
                secretKey.getBytes(StandardCharsets.UTF_8),
                algorithm);
        mac.init(keySpec);
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }

    private String resolveSignatureSecret(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object sessionToken = session.getAttribute(AuthController.SESSION_SIGNATURE_TOKEN_KEY);
            if (sessionToken instanceof String token && !token.isBlank()) {
                return token;
            }
        }
        return properties.getSecretKey();
    }

    private long parseTimestamp(String timestampHeader) {
        try {
            return Long.parseLong(timestampHeader);
        } catch (NumberFormatException ignored) {
            return Instant.parse(timestampHeader).getEpochSecond();
        }
    }

    private void writeErrorResponse(HttpServletResponse response, int status, String message)
            throws IOException {
        ApiResponse.ApiError apiError = new ApiResponse.ApiError(null, "SIGNATURE_VERIFICATION_FAILED", message);
        ApiResponse<Void> body = ApiResponse.error(message, List.of(apiError));

        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }

    private void purgeExpiredNonces() {
        long now = System.currentTimeMillis();
        int before = nonceStore.size();
        nonceStore.entrySet().removeIf(entry -> entry.getValue() < now);
        int removed = before - nonceStore.size();
        if (removed > 0) {
            log.debug("Purged {} expired nonces ({} remaining)", removed, nonceStore.size());
        }
    }
}
