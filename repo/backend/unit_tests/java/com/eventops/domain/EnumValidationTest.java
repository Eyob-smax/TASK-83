package com.eventops.domain;

import com.eventops.domain.user.RoleType;
import com.eventops.domain.user.AccountStatus;
import com.eventops.domain.event.SessionStatus;
import com.eventops.domain.registration.RegistrationStatus;
import com.eventops.domain.checkin.CheckInStatus;
import com.eventops.domain.notification.NotificationType;
import com.eventops.domain.notification.SendStatus;
import com.eventops.domain.finance.*;
import com.eventops.domain.audit.AuditActionType;
import com.eventops.domain.backup.BackupStatus;
import com.eventops.domain.importing.ImportMode;
import com.eventops.domain.importing.ImportJobStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnumValidationTest {

    @Test
    void roleType_hasFourRoles() {
        assertEquals(4, RoleType.values().length);
        assertNotNull(RoleType.valueOf("ATTENDEE"));
        assertNotNull(RoleType.valueOf("EVENT_STAFF"));
        assertNotNull(RoleType.valueOf("FINANCE_MANAGER"));
        assertNotNull(RoleType.valueOf("SYSTEM_ADMIN"));
    }

    @Test
    void sessionStatus_hasSevenStates() {
        assertEquals(7, SessionStatus.values().length);
    }

    @Test
    void registrationStatus_hasFiveStates() {
        assertEquals(5, RegistrationStatus.values().length);
    }

    @Test
    void checkInStatus_hasEightOutcomes() {
        assertEquals(8, CheckInStatus.values().length);
    }

    @Test
    void notificationType_hasNineTypes() {
        assertEquals(9, NotificationType.values().length);
    }

    @Test
    void sendStatus_hasFourStates() {
        assertEquals(4, SendStatus.values().length);
    }

    @Test
    void allocationMethod_hasThreeMethods() {
        assertEquals(3, AllocationMethod.values().length);
        assertNotNull(AllocationMethod.valueOf("PROPORTIONAL"));
        assertNotNull(AllocationMethod.valueOf("FIXED"));
        assertNotNull(AllocationMethod.valueOf("TIERED"));
    }

    @Test
    void revenueRecognition_hasTwoMethods() {
        assertEquals(2, RevenueRecognitionMethod.values().length);
        assertNotNull(RevenueRecognitionMethod.valueOf("IMMEDIATE"));
        assertNotNull(RevenueRecognitionMethod.valueOf("OVER_SESSION_DATES"));
    }

    @Test
    void auditActionType_coversAllDomains() {
        AuditActionType[] actions = AuditActionType.values();
        assertTrue(actions.length >= 30, "Should have at least 30 audit action types");
    }

    @Test
    void backupStatus_hasFiveStates() {
        // SCHEDULED, IN_PROGRESS, COMPLETED, FAILED, EXPIRED
        assertEquals(5, BackupStatus.values().length);
        assertNotNull(BackupStatus.valueOf("EXPIRED"),
                "EXPIRED status required so retention cleanup is semantically distinct from failure");
    }

    @Test
    void importMode_hasTwoModes() {
        assertEquals(2, ImportMode.values().length);
    }

    @Test
    void importJobStatus_hasFiveStates() {
        assertEquals(5, ImportJobStatus.values().length);
    }

    @Test
    void invalidEnum_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> RoleType.valueOf("INVALID"));
    }
}
