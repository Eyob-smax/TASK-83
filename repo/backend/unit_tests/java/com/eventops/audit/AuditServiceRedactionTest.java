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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceRedactionTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    @Mock
    private FieldDiffRepository fieldDiffRepository;

    @InjectMocks
    private AuditService auditService;

    @Test
    void logWithDiffs_redactsSensitiveFieldValues() {
        when(auditEventRepository.save(any(AuditEvent.class))).thenAnswer(invocation -> {
            AuditEvent event = invocation.getArgument(0);
            event.setId("audit-1");
            return event;
        });

        auditService.logWithDiffs(
                AuditActionType.USER_ROLE_CHANGED,
                "admin-1",
                "Admin",
                "WEB",
                "User",
                "user-1",
                "Updated user",
                List.of(
                        new FieldChange("contactInfo", "alice@example.com", "bob@example.com"),
                        new FieldChange("displayName", "Alice", "Bob")
                )
        );

        ArgumentCaptor<FieldDiff> diffCaptor = ArgumentCaptor.forClass(FieldDiff.class);
        verify(fieldDiffRepository, times(2)).save(diffCaptor.capture());

        FieldDiff contactInfoDiff = diffCaptor.getAllValues().get(0);
        FieldDiff displayNameDiff = diffCaptor.getAllValues().get(1);

        assertEquals("[REDACTED]", contactInfoDiff.getOldValue());
        assertEquals("[REDACTED]", contactInfoDiff.getNewValue());
        assertEquals("Alice", displayNameDiff.getOldValue());
        assertEquals("Bob", displayNameDiff.getNewValue());
    }
}
