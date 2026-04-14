package com.eventops.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for per-user rate limiting.
 *
 * <p>Binds to the {@code eventops.security.rate-limit} prefix in
 * {@code application.yml}.</p>
 */
@Configuration
@ConfigurationProperties(prefix = "eventops.security.rate-limit")
public class RateLimitProperties {

    /**
     * Maximum number of requests an authenticated user may make per
     * 60-second sliding window. Defaults to 60.
     */
    private int requestsPerMinute = 60;

    public int getRequestsPerMinute() {
        return requestsPerMinute;
    }

    public void setRequestsPerMinute(int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }
}
