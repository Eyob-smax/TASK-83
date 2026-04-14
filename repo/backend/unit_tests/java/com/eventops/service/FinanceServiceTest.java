package com.eventops.service;

import com.eventops.audit.logging.AuditService;
import com.eventops.common.dto.ConflictType;
import com.eventops.common.exception.BusinessException;
import com.eventops.domain.finance.AccountingPeriod;
import com.eventops.domain.finance.AllocationLineItem;
import com.eventops.domain.finance.AllocationMethod;
import com.eventops.domain.finance.AllocationRule;
import com.eventops.domain.finance.CostCenter;
import com.eventops.domain.finance.PeriodStatus;
import com.eventops.domain.finance.PostingJournal;
import com.eventops.domain.finance.PostingStatus;
import com.eventops.domain.finance.RevenueRecognitionMethod;
import com.eventops.repository.event.EventSessionRepository;
import com.eventops.repository.finance.AccountRepository;
import com.eventops.repository.finance.AccountingPeriodRepository;
import com.eventops.repository.finance.AllocationLineItemRepository;
import com.eventops.repository.finance.AllocationRuleRepository;
import com.eventops.repository.finance.CostCenterRepository;
import com.eventops.repository.finance.PostingJournalRepository;
import com.eventops.service.finance.FinanceService;
import com.eventops.service.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FinanceService}: posting execution, period validation,
 * rule versioning, and posting reversal.
 */
@ExtendWith(MockitoExtension.class)
class FinanceServiceTest {

    @Mock
    private AccountingPeriodRepository accountingPeriodRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CostCenterRepository costCenterRepository;

    @Mock
    private AllocationRuleRepository allocationRuleRepository;

    @Mock
    private PostingJournalRepository postingJournalRepository;

    @Mock
    private AllocationLineItemRepository allocationLineItemRepository;

    @Mock
    private EventSessionRepository eventSessionRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private FinanceService financeService;

    // ------------------------------------------------------------------
    // executePosting()
    // ------------------------------------------------------------------

    @Test
    void executePosting_success_createsJournalAndLineItems() {
        String periodId = "period-1";
        String ruleId = "rule-1";
        BigDecimal totalAmount = new BigDecimal("1000.00");

        AccountingPeriod period = buildPeriod(periodId, PeriodStatus.OPEN);
        AllocationRule rule = buildRule(ruleId, AllocationMethod.PROPORTIONAL,
                RevenueRecognitionMethod.IMMEDIATE, 1);

        // Configure cost center weights in rule config
        rule.setRuleConfig("{\"cc-1\": 0.6, \"cc-2\": 0.4}");

        when(accountingPeriodRepository.findById(periodId)).thenReturn(Optional.of(period));
        when(allocationRuleRepository.findById(ruleId)).thenReturn(Optional.of(rule));
        when(postingJournalRepository.save(any(PostingJournal.class)))
                .thenAnswer(invocation -> {
                    PostingJournal j = invocation.getArgument(0);
                    if (j.getId() == null) j.setId("posting-1");
                    return j;
                });
        when(allocationLineItemRepository.save(any(AllocationLineItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PostingJournal result = financeService.executePosting(
                periodId, null, ruleId, totalAmount, "Test posting", "admin-1");

        assertNotNull(result);
        assertEquals(PostingStatus.POSTED, result.getStatus());
        assertNotNull(result.getPostedAt());
        assertEquals(totalAmount, result.getTotalAmount());

        // Verify posting journal was saved twice: once as DRAFT, once as POSTED
        verify(postingJournalRepository, times(2)).save(any(PostingJournal.class));

        // Verify line items were created (one per cost center in the weights map)
        verify(allocationLineItemRepository, atLeastOnce()).save(any(AllocationLineItem.class));

        // Verify audit was logged with diffs
        verify(auditService).logWithDiffs(any(), eq("admin-1"), anyString(), anyString(),
                eq("PostingJournal"), anyString(), anyString(), any());

        // Verify notification was sent
        verify(notificationService).sendNotification(eq("admin-1"), any(), anyString(), any());
    }

    @Test
    void executePosting_failure_closedPeriod() {
        String periodId = "period-closed";
        AccountingPeriod period = buildPeriod(periodId, PeriodStatus.CLOSED);

        when(accountingPeriodRepository.findById(periodId)).thenReturn(Optional.of(period));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> financeService.executePosting(
                        periodId, null, "rule-1", new BigDecimal("500.00"),
                        "Should fail", "admin-1"));

        assertEquals("PERIOD_CLOSED", ex.getErrorCode());
        assertEquals(ConflictType.PERIOD_CLOSED, ex.getConflictType());

        verify(postingJournalRepository, never()).save(any());
    }

    @Test
    void executePosting_failure_periodNotFound() {
        when(accountingPeriodRepository.findById("missing")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> financeService.executePosting(
                        "missing", null, "rule-1", new BigDecimal("100.00"),
                        "desc", "admin-1"));

        assertEquals("NOT_FOUND", ex.getErrorCode());
        assertEquals(404, ex.getHttpStatus());
    }

    @Test
    void executePosting_failure_ruleNotFound() {
        String periodId = "period-1";
        AccountingPeriod period = buildPeriod(periodId, PeriodStatus.OPEN);

        when(accountingPeriodRepository.findById(periodId)).thenReturn(Optional.of(period));
        when(allocationRuleRepository.findById("missing-rule")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> financeService.executePosting(
                        periodId, null, "missing-rule", new BigDecimal("100.00"),
                        "desc", "admin-1"));

        assertEquals("NOT_FOUND", ex.getErrorCode());
    }

    // ------------------------------------------------------------------
    // createPeriod()
    // ------------------------------------------------------------------

    @Test
    void createPeriod_validates_endAfterStart() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 5, 1); // end before start

        BusinessException ex = assertThrows(BusinessException.class,
                () -> financeService.createPeriod("Q2 2026", start, end));

        assertEquals("INVALID_DATE_RANGE", ex.getErrorCode());
        assertEquals(422, ex.getHttpStatus());
    }

    @Test
    void createPeriod_validates_endEqualsStart() {
        LocalDate date = LocalDate.of(2026, 6, 1);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> financeService.createPeriod("Same day", date, date));

        assertEquals("INVALID_DATE_RANGE", ex.getErrorCode());
    }

    @Test
    void createPeriod_success_whenEndAfterStart() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 3, 31);

        when(accountingPeriodRepository.save(any(AccountingPeriod.class)))
                .thenAnswer(invocation -> {
                    AccountingPeriod p = invocation.getArgument(0);
                    p.setId("new-period-id");
                    return p;
                });

        AccountingPeriod result = financeService.createPeriod("Q1 2026", start, end);

        assertNotNull(result);
        assertEquals(PeriodStatus.OPEN, result.getStatus());
        assertEquals("Q1 2026", result.getName());
        verify(accountingPeriodRepository).save(any(AccountingPeriod.class));
    }

    // ------------------------------------------------------------------
    // updateRule()
    // ------------------------------------------------------------------

    @Test
    void updateRule_createsNewVersion_deactivatesOld() {
        String ruleId = "rule-v1";
        AllocationRule existing = buildRule(ruleId, AllocationMethod.FIXED,
                RevenueRecognitionMethod.IMMEDIATE, 1);

        when(allocationRuleRepository.findById(ruleId)).thenReturn(Optional.of(existing));
        when(allocationRuleRepository.save(any(AllocationRule.class)))
                .thenAnswer(invocation -> {
                    AllocationRule r = invocation.getArgument(0);
                    if (r.getId() == null) r.setId("rule-v2-id");
                    return r;
                });

        AllocationRule updated = financeService.updateRule(
                ruleId, "Updated Name", AllocationMethod.PROPORTIONAL,
                RevenueRecognitionMethod.OVER_SESSION_DATES,
                "acct-2", "cc-2", "{}", "admin-1");

        // Old rule should be deactivated
        assertFalse(existing.isActive());

        // New rule should have incremented version
        assertEquals(2, updated.getVersion());
        assertTrue(updated.isActive());
        assertEquals("Updated Name", updated.getName());
        assertEquals(AllocationMethod.PROPORTIONAL, updated.getAllocationMethod());

        // Save should be called twice: once for deactivating old, once for new version
        verify(allocationRuleRepository, times(2)).save(any(AllocationRule.class));
    }

    @Test
    void updateRule_ruleNotFound_throwsException() {
        when(allocationRuleRepository.findById("missing")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> financeService.updateRule("missing", "name", AllocationMethod.FIXED,
                        RevenueRecognitionMethod.IMMEDIATE, "a", "c", "{}", "admin"));

        assertEquals("NOT_FOUND", ex.getErrorCode());
    }

    // ------------------------------------------------------------------
    // reversePosting()
    // ------------------------------------------------------------------

    @Test
    void reversePosting_setsReversedStatus() {
        String postingId = "posting-100";
        PostingJournal posting = new PostingJournal();
        posting.setId(postingId);
        posting.setStatus(PostingStatus.POSTED);
        posting.setPeriodId("period-1");
        posting.setRuleId("rule-1");
        posting.setTotalAmount(new BigDecimal("500.00"));
        posting.setPostedBy("admin-1");

        when(postingJournalRepository.findById(postingId)).thenReturn(Optional.of(posting));
        when(postingJournalRepository.save(any(PostingJournal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PostingJournal result = financeService.reversePosting(postingId, "admin-2");

        assertEquals(PostingStatus.REVERSED, result.getStatus());
        assertNotNull(result.getReversedAt());

        verify(auditService).log(any(), eq("admin-2"), anyString(), anyString(),
                eq("PostingJournal"), eq(postingId), anyString());
    }

    @Test
    void reversePosting_notFound_throwsException() {
        when(postingJournalRepository.findById("missing")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> financeService.reversePosting("missing", "admin-1"));

        assertEquals("NOT_FOUND", ex.getErrorCode());
        assertEquals(404, ex.getHttpStatus());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private AccountingPeriod buildPeriod(String id, PeriodStatus status) {
        AccountingPeriod period = new AccountingPeriod();
        period.setId(id);
        period.setName("Test Period");
        period.setStartDate(LocalDate.of(2026, 1, 1));
        period.setEndDate(LocalDate.of(2026, 3, 31));
        period.setStatus(status);
        return period;
    }

    private AllocationRule buildRule(String id, AllocationMethod method,
                                     RevenueRecognitionMethod recognition, int version) {
        AllocationRule rule = new AllocationRule();
        rule.setId(id);
        rule.setName("Test Rule");
        rule.setAllocationMethod(method);
        rule.setRecognitionMethod(recognition);
        rule.setAccountId("acct-1");
        rule.setCostCenterId("cc-1");
        rule.setVersion(version);
        rule.setActive(true);
        rule.setCreatedBy("admin-1");
        return rule;
    }
}
