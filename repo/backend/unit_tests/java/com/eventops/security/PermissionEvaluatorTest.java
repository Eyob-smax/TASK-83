package com.eventops.security;

import com.eventops.domain.user.RoleType;
import com.eventops.security.rbac.PermissionEvaluator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PermissionEvaluatorTest {

    private final PermissionEvaluator evaluator = new PermissionEvaluator();

    @Test
    void canAccessRegistration_ownerAllowed() {
        assertTrue(evaluator.canAccessRegistration("u1", "u1", RoleType.ATTENDEE));
    }

    @Test
    void canAccessRegistration_nonOwnerAttendeeDenied() {
        assertFalse(evaluator.canAccessRegistration("u1", "u2", RoleType.ATTENDEE));
    }

    @Test
    void canAccessRegistration_staffAllowedAsNonOwner() {
        assertTrue(evaluator.canAccessRegistration("u1", "staff-1", RoleType.EVENT_STAFF));
    }

    @Test
    void canAccessRegistration_adminAllowedAsNonOwner() {
        assertTrue(evaluator.canAccessRegistration("u1", "admin-1", RoleType.SYSTEM_ADMIN));
    }

    @Test
    void canAccessFinance_allowedForFinanceManagerAndAdmin() {
        assertTrue(evaluator.canAccessFinance(RoleType.FINANCE_MANAGER));
        assertTrue(evaluator.canAccessFinance(RoleType.SYSTEM_ADMIN));
    }

    @Test
    void canAccessFinance_deniedForAttendeeAndStaff() {
        assertFalse(evaluator.canAccessFinance(RoleType.ATTENDEE));
        assertFalse(evaluator.canAccessFinance(RoleType.EVENT_STAFF));
    }

    @Test
    void canAccessAudit_onlyAdmin() {
        assertTrue(evaluator.canAccessAudit(RoleType.SYSTEM_ADMIN));
        assertFalse(evaluator.canAccessAudit(RoleType.FINANCE_MANAGER));
        assertFalse(evaluator.canAccessAudit(RoleType.EVENT_STAFF));
        assertFalse(evaluator.canAccessAudit(RoleType.ATTENDEE));
    }

    @Test
    void canAccessImport_allowedForStaffAndAdmin() {
        assertTrue(evaluator.canAccessImport(RoleType.EVENT_STAFF));
        assertTrue(evaluator.canAccessImport(RoleType.SYSTEM_ADMIN));
        assertFalse(evaluator.canAccessImport(RoleType.FINANCE_MANAGER));
        assertFalse(evaluator.canAccessImport(RoleType.ATTENDEE));
    }

    @Test
    void isStaffOrAdmin_returnsCorrectBoolean() {
        assertTrue(evaluator.isStaffOrAdmin(RoleType.EVENT_STAFF));
        assertTrue(evaluator.isStaffOrAdmin(RoleType.SYSTEM_ADMIN));
        assertFalse(evaluator.isStaffOrAdmin(RoleType.ATTENDEE));
        assertFalse(evaluator.isStaffOrAdmin(RoleType.FINANCE_MANAGER));
    }

    @Test
    void canAccessRegistration_withNullUserIds_matchesOnlyWhenBothNull() {
        assertTrue(evaluator.canAccessRegistration(null, null, RoleType.ATTENDEE));
        assertFalse(evaluator.canAccessRegistration("u1", null, RoleType.ATTENDEE));
        assertFalse(evaluator.canAccessRegistration(null, "u1", RoleType.ATTENDEE));
    }
}
