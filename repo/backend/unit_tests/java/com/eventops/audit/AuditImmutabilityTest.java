package com.eventops.audit;

import com.eventops.audit.logging.AuditService;
import com.eventops.audit.logging.FieldChange;
import com.eventops.domain.audit.AuditActionType;
import com.eventops.domain.audit.AuditEvent;
import com.eventops.domain.audit.FieldDiff;
import com.eventops.repository.audit.AuditEventRepository;
import com.eventops.repository.audit.FieldDiffRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests verifying the immutability contract of {@link AuditService}:
 * audit records are INSERT-only with no update or delete operations.
 */
@ExtendWith(MockitoExtension.class)
class AuditImmutabilityTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    @Mock
    private FieldDiffRepository fieldDiffRepository;

    @InjectMocks
    private AuditService auditService;

    // ------------------------------------------------------------------
    // log() — INSERT-only
    // ------------------------------------------------------------------

    @Test
    void logEvent_insertsNew_neverUpdates() {
        when(auditEventRepository.save(any(AuditEvent.class)))
                .thenAnswer(invocation -> {
                    AuditEvent event = invocation.getArgument(0);
                    // Simulate @PrePersist: id is null before save (new entity)
                    if (event.getId() == null) {
                        event.setId("generated-id");
                    }
                    return event;
                });

        AuditEvent result = auditService.log(
                AuditActionType.LOGIN_SUCCESS,
                "operator-1", "Operator Name", "WEB",
                "User", "user-1", "User logged in");

        assertNotNull(result);
        assertEquals("generated-id", result.getId());

        // Verify save was called exactly once with a new entity
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository, times(1)).save(captor.capture());

        AuditEvent savedEvent = captor.getValue();
        assertEquals(AuditActionType.LOGIN_SUCCESS, savedEvent.getActionType());
        assertEquals("operator-1", savedEvent.getOperatorId());
        assertEquals("User", savedEvent.getEntityType());
        assertEquals("user-1", savedEvent.getEntityId());

        // Verify no delete or deleteAll was called
        verify(auditEventRepository, never()).delete(any(AuditEvent.class));
        verify(auditEventRepository, never()).deleteById(any());
        verify(auditEventRepository, never()).deleteAll();
    }

    // ------------------------------------------------------------------
    // logWithDiffs() — creates both event and diff records atomically
    // ------------------------------------------------------------------

    @Test
    void logWithDiffs_createsDiffRecords() {
        when(auditEventRepository.save(any(AuditEvent.class)))
                .thenAnswer(invocation -> {
                    AuditEvent event = invocation.getArgument(0);
                    event.setId("event-id");
                    return event;
                });
        when(fieldDiffRepository.save(any(FieldDiff.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<FieldChange> changes = List.of(
                new FieldChange("status", "DRAFT", "POSTED"),
                new FieldChange("amount", null, "1000.00"),
                new FieldChange("period", "Q1", "Q2")
        );

        AuditEvent result = auditService.logWithDiffs(
                AuditActionType.POSTING_CREATED,
                "admin-1", "Admin", "SYSTEM",
                "PostingJournal", "posting-1",
                "Posting created with diffs",
                changes);

        assertNotNull(result);

        // Verify audit event was saved once
        verify(auditEventRepository, times(1)).save(any(AuditEvent.class));

        // Verify exactly 3 field diffs were saved
        ArgumentCaptor<FieldDiff> diffCaptor = ArgumentCaptor.forClass(FieldDiff.class);
        verify(fieldDiffRepository, times(3)).save(diffCaptor.capture());

        List<FieldDiff> savedDiffs = diffCaptor.getAllValues();
        assertEquals(3, savedDiffs.size());

        // All diffs should reference the audit event
        for (FieldDiff diff : savedDiffs) {
            assertEquals("event-id", diff.getAuditEventId());
        }

        // Verify field names
        List<String> fieldNames = savedDiffs.stream()
                .map(FieldDiff::getFieldName)
                .toList();
        assertTrue(fieldNames.contains("status"));
        assertTrue(fieldNames.contains("amount"));
        assertTrue(fieldNames.contains("period"));

        // Verify old/new values for the status change
        FieldDiff statusDiff = savedDiffs.stream()
                .filter(d -> "status".equals(d.getFieldName()))
                .findFirst()
                .orElseThrow();
        assertEquals("DRAFT", statusDiff.getOldValue());
        assertEquals("POSTED", statusDiff.getNewValue());
    }

    @Test
    void logWithDiffs_nullFieldChanges_createsEventOnly() {
        when(auditEventRepository.save(any(AuditEvent.class)))
                .thenAnswer(invocation -> {
                    AuditEvent event = invocation.getArgument(0);
                    event.setId("event-id-2");
                    return event;
                });

        AuditEvent result = auditService.logWithDiffs(
                AuditActionType.LOGIN_SUCCESS,
                "op-1", "Op Name", "SYSTEM",
                "User", "user-1", "Login event", null);

        assertNotNull(result);
        verify(auditEventRepository, times(1)).save(any(AuditEvent.class));
        verify(fieldDiffRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // AuditEvent entity immutability — no setCreatedAt
    // ------------------------------------------------------------------

    @Test
    void auditEvent_hasNoSetCreatedAt() {
        // Verify that AuditEvent does not expose a public setCreatedAt method.
        // The createdAt field should only be set by the @PrePersist callback.
        Method[] methods = AuditEvent.class.getMethods();
        boolean hasSetCreatedAt = Arrays.stream(methods)
                .anyMatch(m -> "setCreatedAt".equals(m.getName()));

        assertFalse(hasSetCreatedAt,
                "AuditEvent should not have a public setCreatedAt method; " +
                "createdAt must only be set via @PrePersist for immutability");
    }

    @Test
    void auditEvent_createdAtColumn_isNotUpdatable() {
        // Verify the @Column annotation on createdAt has updatable = false.
        // This is a structural test confirming the JPA mapping enforces immutability.
        try {
            var field = AuditEvent.class.getDeclaredField("createdAt");
            var columnAnnotation = field.getAnnotation(
                    jakarta.persistence.Column.class);
            assertNotNull(columnAnnotation, "createdAt should have @Column annotation");
            assertFalse(columnAnnotation.updatable(),
                    "createdAt @Column should have updatable=false");
        } catch (NoSuchFieldException e) {
            fail("AuditEvent should have a createdAt field");
        }
    }

    @Test
    void fieldDiff_hasNoSetCreatedAt() {
        // Verify that FieldDiff also does not expose a public setCreatedAt method.
        Method[] methods = FieldDiff.class.getMethods();
        boolean hasSetCreatedAt = Arrays.stream(methods)
                .anyMatch(m -> "setCreatedAt".equals(m.getName()));

        assertFalse(hasSetCreatedAt,
                "FieldDiff should not have a public setCreatedAt method; " +
                "createdAt must only be set via @PrePersist for immutability");
    }

    // ------------------------------------------------------------------
    // AuditService API surface — no update/delete methods
    // ------------------------------------------------------------------

    @Test
    void auditService_hasNoUpdateOrDeleteMethods() {
        Method[] methods = AuditService.class.getDeclaredMethods();
        for (Method method : methods) {
            String name = method.getName().toLowerCase();
            assertFalse(name.startsWith("update"),
                    "AuditService should not have update methods, found: " + method.getName());
            assertFalse(name.startsWith("delete"),
                    "AuditService should not have delete methods, found: " + method.getName());
        }
    }
}
