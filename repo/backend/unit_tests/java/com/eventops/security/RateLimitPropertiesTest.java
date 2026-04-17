package com.eventops.security;

import com.eventops.security.ratelimit.RateLimitProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimitPropertiesTest {

    @Test
    void defaultRequestsPerMinute_is60() {
        RateLimitProperties props = new RateLimitProperties();
        assertEquals(60, props.getRequestsPerMinute());
    }

    @Test
    void setRequestsPerMinute_roundTrip() {
        RateLimitProperties props = new RateLimitProperties();
        props.setRequestsPerMinute(120);
        assertEquals(120, props.getRequestsPerMinute());
    }

    @Test
    void setRequestsPerMinute_allowsZero() {
        RateLimitProperties props = new RateLimitProperties();
        props.setRequestsPerMinute(0);
        assertEquals(0, props.getRequestsPerMinute());
    }
}
