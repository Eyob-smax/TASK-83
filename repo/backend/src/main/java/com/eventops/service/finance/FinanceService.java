package com.eventops.service.finance;

import com.eventops.audit.logging.AuditService;
import com.eventops.audit.logging.FieldChange;
import com.eventops.common.dto.ConflictType;
import com.eventops.common.exception.BusinessException;
import com.eventops.domain.audit.AuditActionType;
import com.eventops.domain.event.EventSession;
import com.eventops.domain.finance.Account;
import com.eventops.domain.finance.AccountingPeriod;
import com.eventops.domain.finance.AllocationLineItem;
import com.eventops.domain.finance.AllocationMethod;
import com.eventops.domain.finance.AllocationRule;
import com.eventops.domain.finance.CostCenter;
import com.eventops.domain.finance.PeriodStatus;
import com.eventops.domain.finance.PostingJournal;
import com.eventops.domain.finance.PostingStatus;
import com.eventops.domain.finance.RevenueRecognitionMethod;
import com.eventops.domain.notification.NotificationType;
import com.eventops.finance.allocation.AllocationCalculator;
import com.eventops.finance.allocation.AllocationEngine;
import com.eventops.finance.allocation.AllocationResult;
import com.eventops.finance.recognition.RevenueRecognizer;
import com.eventops.finance.rules.RuleVersionManager;
import com.eventops.repository.event.EventSessionRepository;
import com.eventops.repository.finance.AccountRepository;
import com.eventops.repository.finance.AccountingPeriodRepository;
import com.eventops.repository.finance.AllocationLineItemRepository;
import com.eventops.repository.finance.AllocationRuleRepository;
import com.eventops.repository.finance.CostCenterRepository;
import com.eventops.repository.finance.PostingJournalRepository;
import com.eventops.service.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates all finance operations: accounting periods, chart of accounts,
 * cost centers, allocation rules (with versioning), posting execution with
 * allocation engine integration, and posting reversal.
 */
@Service
@Transactional
public class FinanceService {

    private static final Logger log = LoggerFactory.getLogger(FinanceService.class);

    private final AccountingPeriodRepository accountingPeriodRepository;
    private final AccountRepository accountRepository;
    private final CostCenterRepository costCenterRepository;
    private final AllocationRuleRepository allocationRuleRepository;
    private final PostingJournalRepository postingJournalRepository;
    private final AllocationLineItemRepository allocationLineItemRepository;
    private final EventSessionRepository eventSessionRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    private final AllocationEngine allocationEngine = new AllocationEngine();
    private final RevenueRecognizer revenueRecognizer = new RevenueRecognizer();
    private final RuleVersionManager ruleVersionManager = new RuleVersionManager();

    public FinanceService(AccountingPeriodRepository accountingPeriodRepository,
                          AccountRepository accountRepository,
                          CostCenterRepository costCenterRepository,
                          AllocationRuleRepository allocationRuleRepository,
                          PostingJournalRepository postingJournalRepository,
                          AllocationLineItemRepository allocationLineItemRepository,
                          EventSessionRepository eventSessionRepository,
                          AuditService auditService,
                          NotificationService notificationService) {
        this.accountingPeriodRepository = accountingPeriodRepository;
        this.accountRepository = accountRepository;
        this.costCenterRepository = costCenterRepository;
        this.allocationRuleRepository = allocationRuleRepository;
        this.postingJournalRepository = postingJournalRepository;
        this.allocationLineItemRepository = allocationLineItemRepository;
        this.eventSessionRepository = eventSessionRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    // ------------------------------------------------------------------
    // Accounting Periods
    // ------------------------------------------------------------------

    /**
     * Returns all accounting periods.
     */
    @Transactional(readOnly = true)
    public List<AccountingPeriod> listPeriods() {
        return accountingPeriodRepository.findAll();
    }

    /**
     * Creates a new accounting period with OPEN status.
     *
     * @param name      the period name
     * @param startDate the period start date
     * @param endDate   the period end date (must be after startDate)
     * @return the persisted accounting period
     * @throws BusinessException if endDate is not after startDate
     */
    public AccountingPeriod createPeriod(String name, LocalDate startDate, LocalDate endDate) {
        if (!endDate.isAfter(startDate)) {
            throw new BusinessException("End date must be after start date", 422, "INVALID_DATE_RANGE");
        }

        AccountingPeriod period = new AccountingPeriod();
        period.setName(name);
        period.setStartDate(startDate);
        period.setEndDate(endDate);
        period.setStatus(PeriodStatus.OPEN);

        AccountingPeriod saved = accountingPeriodRepository.save(period);

        auditService.log(AuditActionType.ACCOUNTING_PERIOD_OPENED,
                "SYSTEM", "FinanceService", "SYSTEM",
                "AccountingPeriod", saved.getId(),
                "Accounting period opened: " + name + " (" + startDate + " to " + endDate + ")");

        log.info("Accounting period created: id={}, name={}", saved.getId(), name);
        return saved;
    }

    /**
     * Closes an existing accounting period.
     *
     * @param periodId the period identifier
     * @return the updated accounting period
     * @throws BusinessException if the period does not exist
     */
    public AccountingPeriod closePeriod(String periodId) {
        AccountingPeriod period = accountingPeriodRepository.findById(periodId)
                .orElseThrow(() -> new BusinessException("Accounting period not found", 404, "NOT_FOUND"));

        period.setStatus(PeriodStatus.CLOSED);
        AccountingPeriod saved = accountingPeriodRepository.save(period);

        auditService.log(AuditActionType.ACCOUNTING_PERIOD_CLOSED,
                "SYSTEM", "FinanceService", "SYSTEM",
                "AccountingPeriod", saved.getId(),
                "Accounting period closed: " + period.getName());

        log.info("Accounting period closed: id={}, name={}", saved.getId(), period.getName());
        return saved;
    }

    // ------------------------------------------------------------------
    // Chart of Accounts
    // ------------------------------------------------------------------

    /**
     * Returns all accounts.
     */
    @Transactional(readOnly = true)
    public List<Account> listAccounts() {
        return accountRepository.findAll();
    }

    /**
     * Creates a new account in the chart of accounts.
     *
     * @param accountCode the unique account code
     * @param name        the account name
     * @param description optional description
     * @param parentId    optional parent account ID for hierarchy
     * @param accountType the account type (REVENUE, EXPENSE, ASSET, LIABILITY)
     * @return the persisted account
     * @throws BusinessException if the account code already exists
     */
    public Account createAccount(String accountCode, String name, String description,
                                 String parentId, String accountType) {
        if (accountRepository.existsByAccountCode(accountCode)) {
            throw new BusinessException("Account code already exists: " + accountCode,
                    409, "DUPLICATE_ACCOUNT_CODE");
        }

        Account account = new Account();
        account.setAccountCode(accountCode);
        account.setName(name);
        account.setDescription(description);
        account.setParentId(parentId);
        account.setAccountType(accountType);

        Account saved = accountRepository.save(account);
        log.info("Account created: id={}, code={}", saved.getId(), accountCode);
        return saved;
    }

    // ------------------------------------------------------------------
    // Cost Centers
    // ------------------------------------------------------------------

    /**
     * Returns all cost centers.
     */
    @Transactional(readOnly = true)
    public List<CostCenter> listCostCenters() {
        return costCenterRepository.findAll();
    }

    /**
     * Creates a new cost center.
     *
     * @param code        the unique cost center code
     * @param name        the cost center name
     * @param description optional description
     * @param centerType  the center type (e.g. VEHICLE_TYPE, INSTRUCTOR_TEAM)
     * @return the persisted cost center
     * @throws BusinessException if the cost center code already exists
     */
    public CostCenter createCostCenter(String code, String name, String description, String centerType) {
        if (costCenterRepository.existsByCode(code)) {
            throw new BusinessException("Cost center code already exists: " + code,
                    409, "DUPLICATE_COST_CENTER_CODE");
        }

        CostCenter costCenter = new CostCenter();
        costCenter.setCode(code);
        costCenter.setName(name);
        costCenter.setDescription(description);
        costCenter.setCenterType(centerType);

        CostCenter saved = costCenterRepository.save(costCenter);
        log.info("Cost center created: id={}, code={}", saved.getId(), code);
        return saved;
    }

    // ------------------------------------------------------------------
    // Allocation Rules
    // ------------------------------------------------------------------

    /**
     * Returns all allocation rules.
     */
    @Transactional(readOnly = true)
    public List<AllocationRule> listRules() {
        return allocationRuleRepository.findAll();
    }

    /**
     * Creates a new allocation rule with version 1 and active status.
     *
     * @param name        the rule name
     * @param method      the allocation method
     * @param recognition the revenue recognition method
     * @param accountId   the target account ID
     * @param costCenterId the default cost center ID
     * @param ruleConfig  JSON configuration for the rule
     * @param createdBy   the user who created the rule
     * @return the persisted allocation rule
     */
    public AllocationRule createRule(String name, AllocationMethod method,
                                    RevenueRecognitionMethod recognition,
                                    String accountId, String costCenterId,
                                    String ruleConfig, String createdBy) {
        AllocationRule rule = new AllocationRule();
        rule.setName(name);
        rule.setAllocationMethod(method);
        rule.setRecognitionMethod(recognition);
        rule.setAccountId(accountId);
        rule.setCostCenterId(costCenterId);
        rule.setRuleConfig(ruleConfig);
        rule.setVersion(1);
        rule.setActive(true);
        rule.setCreatedBy(createdBy);

        AllocationRule saved = allocationRuleRepository.save(rule);

        auditService.log(AuditActionType.ALLOCATION_RULE_CREATED,
                createdBy, createdBy, "SYSTEM",
                "AllocationRule", saved.getId(),
                "Allocation rule created: " + name + " (method=" + method + ")");

        log.info("Allocation rule created: id={}, name={}, version=1", saved.getId(), name);
        return saved;
    }

    /**
     * Updates an allocation rule by deactivating the existing version and creating
     * a new version. This preserves the old version for historical traceability.
     *
     * @param ruleId      the existing rule ID to update
     * @param name        the updated rule name
     * @param method      the updated allocation method
     * @param recognition the updated revenue recognition method
     * @param accountId   the updated account ID
     * @param costCenterId the updated cost center ID
     * @param ruleConfig  the updated JSON configuration
     * @param updatedBy   the user performing the update
     * @return the new version of the allocation rule
     * @throws BusinessException if the rule does not exist
     */
    public AllocationRule updateRule(String ruleId, String name, AllocationMethod method,
                                    RevenueRecognitionMethod recognition,
                                    String accountId, String costCenterId,
                                    String ruleConfig, String updatedBy) {
        AllocationRule existing = allocationRuleRepository.findById(ruleId)
                .orElseThrow(() -> new BusinessException("Allocation rule not found", 404, "NOT_FOUND"));

        // Deactivate the existing version
        existing.setActive(false);
        allocationRuleRepository.save(existing);

        // Create a new version via RuleVersionManager
        AllocationRule newVersion = ruleVersionManager.createNewVersion(existing);
        newVersion.setName(name);
        newVersion.setAllocationMethod(method);
        newVersion.setRecognitionMethod(recognition);
        newVersion.setAccountId(accountId);
        newVersion.setCostCenterId(costCenterId);
        newVersion.setRuleConfig(ruleConfig);
        newVersion.setCreatedBy(updatedBy);

        AllocationRule saved = allocationRuleRepository.save(newVersion);

        auditService.log(AuditActionType.ALLOCATION_RULE_UPDATED,
                updatedBy, updatedBy, "SYSTEM",
                "AllocationRule", saved.getId(),
                "Allocation rule updated: " + name + " (version " + existing.getVersion()
                        + " -> " + saved.getVersion() + ")");

        log.info("Allocation rule updated: oldId={}, newId={}, version={}",
                ruleId, saved.getId(), saved.getVersion());
        return saved;
    }

    // ------------------------------------------------------------------
    // Posting Execution
    // ------------------------------------------------------------------

    /**
     * Executes a financial posting: validates the period, runs the allocation engine,
     * computes revenue recognition dates, creates line items, and records the result.
     *
     * @param periodId    the accounting period ID (must be OPEN)
     * @param sessionId   optional event session ID for recognition date lookup
     * @param ruleId      the allocation rule ID
     * @param totalAmount the total amount to allocate
     * @param description posting description
     * @param postedBy    the user executing the posting
     * @return the completed posting journal
     * @throws BusinessException if the period is closed or entities are not found
     */
    public PostingJournal executePosting(String periodId, String sessionId, String ruleId,
                                         BigDecimal totalAmount, String description,
                                         String postedBy) {
        // Validate period exists and is OPEN
        AccountingPeriod period = accountingPeriodRepository.findById(periodId)
                .orElseThrow(() -> new BusinessException("Accounting period not found", 404, "NOT_FOUND"));

        if (period.getStatus() != PeriodStatus.OPEN) {
            throw new BusinessException("Period is closed", 422, "PERIOD_CLOSED",
                    ConflictType.PERIOD_CLOSED);
        }

        // Load rule and capture current version
        AllocationRule rule = allocationRuleRepository.findById(ruleId)
                .orElseThrow(() -> new BusinessException("Allocation rule not found", 404, "NOT_FOUND"));

        int ruleVersion = rule.getVersion();

        // Create PostingJournal with rule version snapshot
        PostingJournal posting = new PostingJournal();
        posting.setPeriodId(periodId);
        posting.setSessionId(sessionId);
        posting.setRuleId(ruleId);
        posting.setRuleVersion(ruleVersion);
        posting.setTotalAmount(totalAmount);
        posting.setStatus(PostingStatus.DRAFT);
        posting.setDescription(description);
        posting.setPostedBy(postedBy);

        posting = postingJournalRepository.save(posting);

        // Get calculator for the rule's allocation method
        AllocationCalculator calculator = allocationEngine.getCalculator(rule.getAllocationMethod());

        // Build weights map from active cost centers
        Map<String, BigDecimal> weights = buildWeightsMap(rule);

        // Execute allocation
        List<AllocationResult> results = calculator.allocate(totalAmount, weights, rule.getRuleConfig());

        // Compute revenue recognition period
        LocalDate postingDate = LocalDate.now();
        LocalDate sessionStartDate = null;
        LocalDate sessionEndDate = null;

        if (sessionId != null) {
            EventSession session = eventSessionRepository.findById(sessionId).orElse(null);
            if (session != null) {
                sessionStartDate = session.getStartTime().toLocalDate();
                sessionEndDate = session.getEndTime().toLocalDate();
            }
        }

        RevenueRecognizer.RecognitionPeriod recognitionPeriod =
                revenueRecognizer.computePeriod(rule.getRecognitionMethod(),
                        postingDate, sessionStartDate, sessionEndDate);

        // Create allocation line items
        for (AllocationResult result : results) {
            AllocationLineItem lineItem = new AllocationLineItem();
            lineItem.setPostingId(posting.getId());
            lineItem.setAccountId(rule.getAccountId());
            lineItem.setCostCenterId(result.costCenterId());
            lineItem.setAmount(result.amount());
            lineItem.setDescription(result.description());
            lineItem.setRecognitionStart(recognitionPeriod.startDate());
            lineItem.setRecognitionEnd(recognitionPeriod.endDate());
            allocationLineItemRepository.save(lineItem);
        }

        // Finalize posting
        posting.setStatus(PostingStatus.POSTED);
        posting.setPostedAt(Instant.now());
        PostingJournal saved = postingJournalRepository.save(posting);

        // Audit with field diffs
        List<FieldChange> fieldChanges = List.of(
                new FieldChange("amount", null, totalAmount.toPlainString()),
                new FieldChange("period", null, period.getName()),
                new FieldChange("ruleVersion", null, String.valueOf(ruleVersion))
        );

        auditService.logWithDiffs(AuditActionType.POSTING_CREATED,
                postedBy, postedBy, "SYSTEM",
                "PostingJournal", saved.getId(),
                "Posting created: amount=" + totalAmount + ", period=" + period.getName()
                        + ", rule version=" + ruleVersion,
                fieldChanges);

        // Notify the user
        notificationService.sendNotification(postedBy,
                NotificationType.FINANCE_POSTING_RESULT, saved.getId(),
                Map.of("postingId", saved.getId(),
                        "amount", totalAmount.toPlainString(),
                        "status", "POSTED"));

        log.info("Posting executed: id={}, amount={}, lineItems={}", saved.getId(), totalAmount, results.size());
        return saved;
    }

    /**
     * Lists postings, optionally filtered by period ID.
     *
     * @param periodId optional period ID filter
     * @return list of posting journals
     */
    @Transactional(readOnly = true)
    public List<PostingJournal> listPostings(String periodId) {
        if (periodId != null && !periodId.isBlank()) {
            return postingJournalRepository.findByPeriodId(periodId);
        }
        return postingJournalRepository.findAll();
    }

    /**
     * Returns the allocation line items for a posting.
     *
     * @param postingId the posting journal ID
     * @return list of line items
     */
    @Transactional(readOnly = true)
    public List<AllocationLineItem> getPostingLineItems(String postingId) {
        return allocationLineItemRepository.findByPostingId(postingId);
    }

    /**
     * Reverses an existing posting by marking it as REVERSED.
     *
     * @param postingId  the posting journal ID
     * @param reversedBy the user performing the reversal
     * @return the updated posting journal
     * @throws BusinessException if the posting does not exist
     */
    public PostingJournal reversePosting(String postingId, String reversedBy) {
        PostingJournal posting = postingJournalRepository.findById(postingId)
                .orElseThrow(() -> new BusinessException("Posting not found", 404, "NOT_FOUND"));

        posting.setStatus(PostingStatus.REVERSED);
        posting.setReversedAt(Instant.now());
        PostingJournal saved = postingJournalRepository.save(posting);

        auditService.log(AuditActionType.POSTING_REVERSED,
                reversedBy, reversedBy, "SYSTEM",
                "PostingJournal", saved.getId(),
                "Posting reversed: id=" + postingId);

        log.info("Posting reversed: id={}, reversedBy={}", postingId, reversedBy);
        return saved;
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Builds a weights map for the allocation engine. If the rule has a ruleConfig
     * with cost center weights, those are parsed. Otherwise, active cost centers
     * receive equal weights.
     */
    private Map<String, BigDecimal> buildWeightsMap(AllocationRule rule) {
        Map<String, BigDecimal> weights = new LinkedHashMap<>();

        // Attempt to parse weights from ruleConfig (JSON format: {"costCenterId": weight, ...})
        if (rule.getRuleConfig() != null && !rule.getRuleConfig().isBlank()) {
            try {
                String config = rule.getRuleConfig().trim();
                if (config.startsWith("{") && config.contains(":")) {
                    // Simple JSON-like parsing for cost center weight configuration
                    String inner = config.substring(1, config.length() - 1);
                    String[] pairs = inner.split(",");
                    for (String pair : pairs) {
                        String[] kv = pair.split(":");
                        if (kv.length == 2) {
                            String key = kv[0].trim().replace("\"", "");
                            String value = kv[1].trim().replace("\"", "");
                            if (!key.isEmpty() && !value.isEmpty()) {
                                weights.put(key, new BigDecimal(value));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Could not parse ruleConfig as weights map, falling back to equal weights: {}",
                        e.getMessage());
                weights.clear();
            }
        }

        // Fall back to equal weights across active cost centers
        if (weights.isEmpty()) {
            List<CostCenter> activeCenters = costCenterRepository.findByActiveTrue();
            for (CostCenter center : activeCenters) {
                weights.put(center.getId(), BigDecimal.ONE);
            }
        }

        return weights;
    }
}
