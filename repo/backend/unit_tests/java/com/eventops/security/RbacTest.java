package com.eventops.security;

import com.eventops.domain.user.RoleType;
import com.eventops.security.rbac.PermissionEvaluator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RbacTest {

    private final PermissionEvaluator evaluator = new PermissionEvaluator();

    @Test
    void attendee_canAccessOwnRegistration() {
        assertTrue(evaluator.canAccessRegistration("user1", "user1", RoleType.ATTENDEE));
    }

    @Test
    void attendee_cannotAccessOthersRegistration() {
        assertFalse(evaluator.canAccessRegistration("user2", "user1", RoleType.ATTENDEE));
    }

    @Test
    void staff_canAccessAnyRegistration() {
        assertTrue(evaluator.canAccessRegistration("user2", "user1", RoleType.EVENT_STAFF));
    }

    @Test
    void admin_canAccessAnyRegistration() {
        assertTrue(evaluator.canAccessRegistration("user2", "user1", RoleType.SYSTEM_ADMIN));
    }

    @Test
    void financeManager_canAccessFinance() {
        assertTrue(evaluator.canAccessFinance(RoleType.FINANCE_MANAGER));
    }

    @Test
    void attendee_cannotAccessFinance() {
        assertFalse(evaluator.canAccessFinance(RoleType.ATTENDEE));
    }

    @Test
    void onlyAdmin_canAccessAudit() {
        assertTrue(evaluator.canAccessAudit(RoleType.SYSTEM_ADMIN));
        assertFalse(evaluator.canAccessAudit(RoleType.FINANCE_MANAGER));
        assertFalse(evaluator.canAccessAudit(RoleType.EVENT_STAFF));
        assertFalse(evaluator.canAccessAudit(RoleType.ATTENDEE));
    }

    @Test
    void staff_canAccessImport() {
        assertTrue(evaluator.canAccessImport(RoleType.EVENT_STAFF));
        assertTrue(evaluator.canAccessImport(RoleType.SYSTEM_ADMIN));
        assertFalse(evaluator.canAccessImport(RoleType.ATTENDEE));
    }

    @Test
    void isStaffOrAdmin_correctBoundaries() {
        assertTrue(evaluator.isStaffOrAdmin(RoleType.EVENT_STAFF));
        assertTrue(evaluator.isStaffOrAdmin(RoleType.SYSTEM_ADMIN));
        assertFalse(evaluator.isStaffOrAdmin(RoleType.ATTENDEE));
        assertFalse(evaluator.isStaffOrAdmin(RoleType.FINANCE_MANAGER));
    }
}
