package com.eventops.security.ratelimit;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe sliding window counter for rate limiting.
 *
 * <p>Maintains a queue of request timestamps within a configurable window.
 * The {@link #tryAcquire()} method atomically checks whether the current
 * request would exceed the limit and, if not, records it.</p>
 *
 * <p>This is a package-private class — only {@link RateLimitFilter} creates
 * and manages instances.</p>
 */
class SlidingWindowCounter {

    private final int limit;
    private final long windowMillis;
    private final ConcurrentLinkedQueue<Long> timestamps = new ConcurrentLinkedQueue<>();

    /**
     * Creates a new sliding window counter.
     *
     * @param limit        maximum number of requests allowed within the window
     * @param windowMillis size of the sliding window in milliseconds
     */
    SlidingWindowCounter(int limit, long windowMillis) {
        this.limit = limit;
        this.windowMillis = windowMillis;
    }

    /**
     * Attempts to acquire a permit for the current request.
     *
     * <p>Purges timestamps that have fallen outside the sliding window,
     * then checks whether the number of requests within the window is
     * below the configured limit. If so, the current timestamp is
     * recorded and {@code true} is returned.</p>
     *
     * @return {@code true} if the request is permitted, {@code false} if
     *         the rate limit has been exceeded
     */
    boolean tryAcquire() {
        long now = System.currentTimeMillis();
        long cutoff = now - windowMillis;

        // Purge expired entries from the head of the queue
        while (true) {
            Long head = timestamps.peek();
            if (head == null || head >= cutoff) {
                break;
            }
            timestamps.poll();
        }

        // Check limit — a slight race is acceptable (may allow one or two
        // extra requests under extreme concurrency, which is fine for a
        // best-effort rate limiter)
        if (timestamps.size() >= limit) {
            return false;
        }

        timestamps.add(now);
        return true;
    }

    /**
     * Returns the number of requests currently tracked within the window.
     * Used for diagnostics and the Retry-After calculation.
     */
    int currentCount() {
        long cutoff = System.currentTimeMillis() - windowMillis;
        while (true) {
            Long head = timestamps.peek();
            if (head == null || head >= cutoff) {
                break;
            }
            timestamps.poll();
        }
        return timestamps.size();
    }

    /**
     * Returns the number of seconds until the oldest request in the window
     * expires, which is the minimum wait time before a new permit becomes
     * available.
     *
     * @return seconds until the next permit, or 1 if the window is empty
     */
    long retryAfterSeconds() {
        Long oldest = timestamps.peek();
        if (oldest == null) {
            return 1;
        }
        long expiresAt = oldest + windowMillis;
        long remaining = expiresAt - System.currentTimeMillis();
        return Math.max(1, (remaining + 999) / 1000); // ceil to whole seconds
    }

    int getLimit() {
        return limit;
    }
}
