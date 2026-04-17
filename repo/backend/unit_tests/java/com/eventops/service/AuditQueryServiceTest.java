package com.eventops.service;

import com.eventops.common.exception.BusinessException;
import com.eventops.domain.audit.AuditEvent;
import com.eventops.domain.audit.FieldDiff;
import com.eventops.repository.audit.AuditEventRepository;
import com.eventops.repository.audit.FieldDiffRepository;
import com.eventops.service.audit.AuditQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditQueryServiceTest {

    @Mock AuditEventRepository auditEventRepository;
    @Mock FieldDiffRepository fieldDiffRepository;
    @InjectMocks AuditQueryService service;

    @Test
    void searchLogs_noFilters_returnsAllViaSpec() {
        Pageable pageable = PageRequest.of(0, 20);
        when(auditEventRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<AuditEvent> result = service.searchLogs(null, null, null, null, null, null, pageable);

        assertEquals(0, result.getNumberOfElements());
        verify(auditEventRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void searchLogs_withActionType_includesActionTypeInSpec() {
        Pageable pageable = PageRequest.of(0, 20);
        when(auditEventRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        service.searchLogs("ACCOUNT_CREATED", null, null, null, null, null, pageable);

        verify(auditEventRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void searchLogs_withOperatorId_addsOperatorPredicate() {
        Pageable pageable = PageRequest.of(0, 20);
        when(auditEventRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        service.searchLogs(null, "u1", null, null, null, null, pageable);

        verify(auditEventRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void searchLogs_withDateRange_addsDatePredicates() {
        Pageable pageable = PageRequest.of(0, 20);
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-12-31T23:59:59Z");
        when(auditEventRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        service.searchLogs(null, null, null, null, from, to, pageable);

        verify(auditEventRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void searchLogs_withEntityTypeAndId_addsBoth() {
        Pageable pageable = PageRequest.of(0, 20);
        when(auditEventRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        service.searchLogs(null, null, "User", "u1", null, null, pageable);

        verify(auditEventRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void searchLogs_blankStringsAreIgnored() {
        Pageable pageable = PageRequest.of(0, 20);
        when(auditEventRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        service.searchLogs("", "", "", "", null, null, pageable);

        verify(auditEventRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void getLog_found_returnsAuditEvent() {
        AuditEvent event = new AuditEvent();
        event.setId("a1");
        when(auditEventRepository.findById("a1")).thenReturn(Optional.of(event));

        AuditEvent result = service.getLog("a1");

        assertSame(event, result);
    }

    @Test
    void getLog_notFound_throws404() {
        when(auditEventRepository.findById("missing")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getLog("missing"));
        assertEquals(404, ex.getHttpStatus());
    }

    @Test
    void getFieldDiffs_delegatesToRepository() {
        FieldDiff diff = new FieldDiff();
        when(fieldDiffRepository.findByAuditEventId("a1")).thenReturn(List.of(diff));

        List<FieldDiff> result = service.getFieldDiffs("a1");

        assertEquals(1, result.size());
    }
}
