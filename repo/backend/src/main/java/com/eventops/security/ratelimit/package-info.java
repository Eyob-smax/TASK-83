/**
 * Rate limiting infrastructure.
 *
 * <p>Implements per-user sliding-window rate limiting at the configured
 * baseline (60 requests/minute). Separate anti-bot throttle logic for
 * the login endpoint. Returns HTTP 429 with retry-after information.</p>
 */
package com.eventops.security.ratelimit;
