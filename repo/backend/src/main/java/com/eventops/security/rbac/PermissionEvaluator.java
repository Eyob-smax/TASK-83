package com.eventops.security.rbac;

import com.eventops.domain.user.RoleType;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Custom permission-evaluation helper for data-scope access checks.
 *
 * <p>This is <strong>not</strong> the Spring
 * {@link org.springframework.security.access.PermissionEvaluator} interface;
 * it is an application-level component that encapsulates business rules
 * around who may view or mutate particular resource categories.</p>
 *
 * <p>Designed for injection into services and controllers that need
 * fine-grained, data-level access decisions beyond URL-pattern rules.</p>
 */
@Component
public class PermissionEvaluator {

    /**
     * Determines whether the caller may access a specific registration record.
     *
     * <p>Attendees may only view their own registrations. Event staff and
     * system administrators may access any registration.</p>
     *
     * @param userId        the owner of the registration
     * @param currentUserId the currently authenticated user
     * @param role          the caller's role
     * @return {@code true} if access is permitted
     */
    public boolean canAccessRegistration(String userId, String currentUserId, RoleType role) {
        if (isStaffOrAdmin(role)) {
            return true;
        }
        return Objects.equals(userId, currentUserId);
    }

    /**
     * Checks whether the caller may access finance-related resources.
     *
     * @param role the caller's role
     * @return {@code true} if the role is {@code FINANCE_MANAGER} or {@code SYSTEM_ADMIN}
     */
    public boolean canAccessFinance(RoleType role) {
        return role == RoleType.FINANCE_MANAGER || role == RoleType.SYSTEM_ADMIN;
    }

    /**
     * Checks whether the caller may access audit-log resources.
     *
     * @param role the caller's role
     * @return {@code true} if the role is {@code SYSTEM_ADMIN}
     */
    public boolean canAccessAudit(RoleType role) {
        return role == RoleType.SYSTEM_ADMIN;
    }

    /**
     * Checks whether the caller may trigger data imports.
     *
     * @param role the caller's role
     * @return {@code true} if the role is {@code EVENT_STAFF} or {@code SYSTEM_ADMIN}
     */
    public boolean canAccessImport(RoleType role) {
        return role == RoleType.EVENT_STAFF || role == RoleType.SYSTEM_ADMIN;
    }

    /**
     * Convenience check: is the caller event staff or a system administrator?
     *
     * @param role the caller's role
     * @return {@code true} if the role is {@code EVENT_STAFF} or {@code SYSTEM_ADMIN}
     */
    public boolean isStaffOrAdmin(RoleType role) {
        return role == RoleType.EVENT_STAFF || role == RoleType.SYSTEM_ADMIN;
    }
}
