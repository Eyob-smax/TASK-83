package com.eventops.security.ratelimit;

import com.eventops.common.dto.ApiResponse;
import com.eventops.service.admin.SecuritySettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servlet filter that enforces per-user rate limiting using a sliding window.
 *
 * <p>Each authenticated user is tracked independently via a
 * {@link SlidingWindowCounter} keyed by their username. The sliding window
 * spans 60 seconds, and the maximum number of requests within that window
 * is controlled by {@link RateLimitProperties#getRequestsPerMinute()}.</p>
 *
 * <p>Unauthenticated requests are not rate-limited by this filter — they
 * will be rejected by the authentication filters instead.</p>
 *
 * <p>This filter is ordered after authentication filters so that the
 * {@link SecurityContextHolder} is populated when this filter executes.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final long WINDOW_MILLIS = 60_000L;

    private final SecuritySettingsService securitySettingsService;
    private final ObjectMapper objectMapper;

    /**
     * Per-user sliding window counters. Keys are usernames (from the
     * Spring Security principal).
     */
    private final ConcurrentHashMap<String, SlidingWindowCounter> counters = new ConcurrentHashMap<>();

    public RateLimitFilter(SecuritySettingsService securitySettingsService, ObjectMapper objectMapper) {
        this.securitySettingsService = securitySettingsService;
        this.objectMapper = objectMapper;
        log.info("Rate limit filter initialized");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Only rate-limit authenticated users
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            filterChain.doFilter(request, response);
            return;
        }

        String username = authentication.getName();
        int currentLimit = securitySettingsService.getRateLimitPerMinute();
        SlidingWindowCounter counter = counters.compute(username, (key, existing) -> {
            if (existing == null || existing.getLimit() != currentLimit) {
                return new SlidingWindowCounter(currentLimit, WINDOW_MILLIS);
            }
            return existing;
        });

        if (!counter.tryAcquire()) {
            long retryAfter = counter.retryAfterSeconds();
            log.warn("Rate limit exceeded for user '{}' on {} {} (limit={}/min)",
                    username, request.getMethod(), request.getRequestURI(),
                    currentLimit);

            response.setIntHeader("Retry-After", (int) retryAfter);
            writeErrorResponse(response, retryAfter);
            return;
        }

        // Add rate-limit informational headers
        response.setIntHeader("X-RateLimit-Limit", currentLimit);
        response.setIntHeader("X-RateLimit-Remaining",
                Math.max(0, currentLimit - counter.currentCount()));

        filterChain.doFilter(request, response);
    }

    /**
     * Writes a structured JSON 429 error response using the standard
     * {@link ApiResponse} envelope.
     */
    private void writeErrorResponse(HttpServletResponse response, long retryAfterSeconds)
            throws IOException {

        String message = "Rate limit exceeded. Please retry after " + retryAfterSeconds + " second(s).";
        ApiResponse.ApiError apiError = new ApiResponse.ApiError(null, "RATE_LIMIT_EXCEEDED", message);
        ApiResponse<Void> body = ApiResponse.error(message, List.of(apiError));

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
