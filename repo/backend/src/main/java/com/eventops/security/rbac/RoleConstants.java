package com.eventops.security.rbac;

/**
 * String constants for role names used in {@code @PreAuthorize} SpEL expressions.
 *
 * <p>Spring Security expects the "ROLE_" prefix for {@code hasRole()} checks.
 * These constants centralise the strings so that typos are caught at compile
 * time rather than at runtime.</p>
 *
 * <p>Usage in controller or service methods:</p>
 * <pre>{@code
 * @PreAuthorize(RoleConstants.HAS_SYSTEM_ADMIN)
 * public void deleteUser(String userId) { ... }
 * }</pre>
 */
public final class RoleConstants {

    private RoleConstants() {
        // Utility class — no instantiation
    }

    // ── Role name constants (with ROLE_ prefix as required by Spring Security) ──

    public static final String ROLE_ATTENDEE = "ROLE_ATTENDEE";
    public static final String ROLE_EVENT_STAFF = "ROLE_EVENT_STAFF";
    public static final String ROLE_FINANCE_MANAGER = "ROLE_FINANCE_MANAGER";
    public static final String ROLE_SYSTEM_ADMIN = "ROLE_SYSTEM_ADMIN";

    // ── SpEL expression strings for @PreAuthorize ──

    public static final String HAS_ATTENDEE =
            "hasRole('ATTENDEE')";

    public static final String HAS_EVENT_STAFF =
            "hasRole('EVENT_STAFF')";

    public static final String HAS_FINANCE_MANAGER =
            "hasRole('FINANCE_MANAGER')";

    public static final String HAS_SYSTEM_ADMIN =
            "hasRole('SYSTEM_ADMIN')";

    public static final String HAS_STAFF_OR_ADMIN =
            "hasAnyRole('EVENT_STAFF', 'SYSTEM_ADMIN')";

    public static final String HAS_FINANCE_OR_ADMIN =
            "hasAnyRole('FINANCE_MANAGER', 'SYSTEM_ADMIN')";

    public static final String HAS_EXPORT_ACCESS =
            "hasAnyRole('EVENT_STAFF', 'FINANCE_MANAGER', 'SYSTEM_ADMIN')";
}
