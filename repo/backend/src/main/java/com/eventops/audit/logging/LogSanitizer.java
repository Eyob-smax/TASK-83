package com.eventops.audit.logging;

import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * Utility for scrubbing sensitive data from log messages and parameter maps.
 *
 * <p>This class is stateless; all methods are {@code static}. It prevents
 * accidental leakage of passwords, tokens, secrets, and other sensitive
 * values into application log output.</p>
 */
public final class LogSanitizer {

    /** The replacement string used wherever a sensitive value is detected. */
    private static final String REDACTED = "[REDACTED]";

    /**
     * Key patterns (case-insensitive substrings) that flag a map entry value
     * for redaction.
     */
    public static final Set<String> SENSITIVE_KEYS = Set.of(
            "password",
            "secret",
            "token",
            "key",
            "authorization",
            "signature",
            "contact",
            "fingerprint",
            "hash"
    );

    /**
     * Regex that matches {@code key=value} pairs in free-text log messages
     * where the key is one of the sensitive patterns. The value is everything
     * up to the next whitespace, comma, semicolon, closing bracket, or
     * end-of-string.
     *
     * <p>Examples matched:
     * <ul>
     *   <li>{@code password=s3cret}</li>
     *   <li>{@code token=abc123def}</li>
     *   <li>{@code contactInfo=+1-555-0100}</li>
     *   <li>{@code secret_key=xyz}</li>
     * </ul>
     */
    private static final Pattern SENSITIVE_PAIR_PATTERN;

    static {
        // Build an alternation of all sensitive key stems.
        StringJoiner alternation = new StringJoiner("|");
        for (String key : SENSITIVE_KEYS) {
            alternation.add(Pattern.quote(key));
        }
        // Match key (possibly with surrounding word chars like "passwordHash")
        // followed by =, :, or => and then the value.
        String regex = "(?i)(\\w*(?:" + alternation + ")\\w*)"   // group 1: full key
                     + "\\s*[=:]+>?\\s*"                         // separator
                     + "([^\\s,;\\])}]+)";                       // group 2: value
        SENSITIVE_PAIR_PATTERN = Pattern.compile(regex);
    }

    private LogSanitizer() {
        // utility class — not instantiable
    }

    /**
     * Scans {@code logMessage} for patterns that look like sensitive
     * key-value pairs and replaces the values with {@code [REDACTED]}.
     *
     * @param logMessage the raw log message (may be {@code null})
     * @return the sanitized message, or {@code null} if the input was {@code null}
     */
    public static String sanitize(String logMessage) {
        if (logMessage == null) {
            return null;
        }
        return SENSITIVE_PAIR_PATTERN.matcher(logMessage).replaceAll("$1=" + REDACTED);
    }

    /**
     * Returns a sanitized string representation of {@code params}. Values
     * whose keys match any of the {@link #SENSITIVE_KEYS} patterns
     * (case-insensitive substring match) are replaced with {@code [REDACTED]}.
     *
     * @param params the parameter map (may be {@code null} or empty)
     * @return a human-readable, sanitized string (never {@code null})
     */
    public static String sanitizeMap(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return "{}";
        }
        StringJoiner joiner = new StringJoiner(", ", "{", "}");
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            String displayValue;
            if (isSensitiveKey(key)) {
                displayValue = REDACTED;
            } else {
                displayValue = entry.getValue() == null ? "null" : entry.getValue().toString();
            }
            joiner.add(key + "=" + displayValue);
        }
        return joiner.toString();
    }

    /**
     * Returns {@code true} if the given key matches (case-insensitive
     * substring) any of the {@link #SENSITIVE_KEYS} patterns.
     */
    private static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase();
        for (String sensitive : SENSITIVE_KEYS) {
            if (lower.contains(sensitive)) {
                return true;
            }
        }
        return false;
    }
}
