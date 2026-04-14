package com.eventops.controller.finance;

import com.eventops.common.dto.ApiResponse;
import com.eventops.common.dto.finance.AccountingPeriodRequest;
import com.eventops.common.dto.finance.AllocationRuleRequest;
import com.eventops.common.dto.finance.PostingRequest;
import com.eventops.domain.finance.Account;
import com.eventops.domain.finance.AccountingPeriod;
import com.eventops.domain.finance.AllocationLineItem;
import com.eventops.domain.finance.AllocationMethod;
import com.eventops.domain.finance.AllocationRule;
import com.eventops.domain.finance.CostCenter;
import com.eventops.domain.finance.PostingJournal;
import com.eventops.domain.finance.RevenueRecognitionMethod;
import com.eventops.security.auth.EventOpsUserDetails;
import com.eventops.service.finance.FinanceService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for financial operations: accounting periods, chart of accounts,
 * cost centers, allocation rules, and posting management.
 *
 * <p>All endpoints return the standard {@link ApiResponse} envelope.</p>
 */
@RestController
@RequestMapping("/api/finance")
public class FinanceController {

    private static final Logger log = LoggerFactory.getLogger(FinanceController.class);

    private final FinanceService financeService;

    public FinanceController(FinanceService financeService) {
        this.financeService = financeService;
    }

    // ------------------------------------------------------------------
    // Accounting Periods
    // ------------------------------------------------------------------

    /**
     * Lists all accounting periods.
     *
     * @return 200 with list of accounting periods
     */
    @GetMapping("/periods")
    public ResponseEntity<ApiResponse<List<AccountingPeriod>>> listPeriods() {
        log.debug("GET /api/finance/periods");

        List<AccountingPeriod> periods = financeService.listPeriods();
        return ResponseEntity.ok(ApiResponse.success(periods));
    }

    /**
     * Creates a new accounting period.
     *
     * @param request the accounting period details
     * @return 201 with the created accounting period
     */
    @PostMapping("/periods")
    public ResponseEntity<ApiResponse<AccountingPeriod>> createPeriod(
            @Valid @RequestBody AccountingPeriodRequest request) {
        log.debug("POST /api/finance/periods – name={}", request.getName());

        AccountingPeriod period = financeService.createPeriod(
                request.getName(), request.getStartDate(), request.getEndDate());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(period, "Accounting period created"));
    }

    /**
     * Closes an accounting period.
     *
     * @param id the period identifier
     * @return 200 with the closed accounting period
     */
    @PutMapping("/periods/{id}/close")
    public ResponseEntity<ApiResponse<AccountingPeriod>> closePeriod(@PathVariable String id) {
        log.debug("PUT /api/finance/periods/{}/close", id);

        AccountingPeriod period = financeService.closePeriod(id);
        return ResponseEntity.ok(ApiResponse.success(period, "Accounting period closed"));
    }

    // ------------------------------------------------------------------
    // Chart of Accounts
    // ------------------------------------------------------------------

    /**
     * Lists all accounts in the chart of accounts.
     *
     * @return 200 with list of accounts
     */
    @GetMapping("/accounts")
    public ResponseEntity<ApiResponse<List<Account>>> listAccounts() {
        log.debug("GET /api/finance/accounts");

        List<Account> accounts = financeService.listAccounts();
        return ResponseEntity.ok(ApiResponse.success(accounts));
    }

    /**
     * Creates a new account in the chart of accounts.
     *
     * @param body request body containing accountCode, name, description, parentId, and accountType
     * @return 201 with the created account
     */
    @PostMapping("/accounts")
    public ResponseEntity<ApiResponse<Account>> createAccount(@RequestBody Map<String, String> body) {
        String accountCode = body.get("accountCode");
        String name = body.get("name");
        String description = body.get("description");
        String parentId = body.get("parentId");
        String accountType = body.get("accountType");
        log.debug("POST /api/finance/accounts – code={}, name={}", accountCode, name);

        Account account = financeService.createAccount(accountCode, name, description, parentId, accountType);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(account, "Account created"));
    }

    // ------------------------------------------------------------------
    // Cost Centers
    // ------------------------------------------------------------------

    /**
     * Lists all cost centers.
     *
     * @return 200 with list of cost centers
     */
    @GetMapping("/cost-centers")
    public ResponseEntity<ApiResponse<List<CostCenter>>> listCostCenters() {
        log.debug("GET /api/finance/cost-centers");

        List<CostCenter> costCenters = financeService.listCostCenters();
        return ResponseEntity.ok(ApiResponse.success(costCenters));
    }

    /**
     * Creates a new cost center.
     *
     * @param body request body containing code, name, description, and centerType
     * @return 201 with the created cost center
     */
    @PostMapping("/cost-centers")
    public ResponseEntity<ApiResponse<CostCenter>> createCostCenter(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        String name = body.get("name");
        String description = body.get("description");
        String centerType = body.get("centerType");
        log.debug("POST /api/finance/cost-centers – code={}, name={}", code, name);

        CostCenter costCenter = financeService.createCostCenter(code, name, description, centerType);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(costCenter, "Cost center created"));
    }

    // ------------------------------------------------------------------
    // Allocation Rules
    // ------------------------------------------------------------------

    /**
     * Lists all allocation rules.
     *
     * @return 200 with list of allocation rules
     */
    @GetMapping("/rules")
    public ResponseEntity<ApiResponse<List<AllocationRule>>> listRules() {
        log.debug("GET /api/finance/rules");

        List<AllocationRule> rules = financeService.listRules();
        return ResponseEntity.ok(ApiResponse.success(rules));
    }

    /**
     * Creates a new allocation rule.
     *
     * @param request   the allocation rule details
     * @param principal the authenticated user
     * @return 201 with the created allocation rule
     */
    @PostMapping("/rules")
    public ResponseEntity<ApiResponse<AllocationRule>> createRule(
            @Valid @RequestBody AllocationRuleRequest request,
            @AuthenticationPrincipal EventOpsUserDetails principal) {
        String userId = principal.getUser().getId();
        log.debug("POST /api/finance/rules – name={}, userId={}", request.getName(), userId);

        AllocationRule rule = financeService.createRule(
                request.getName(),
                AllocationMethod.valueOf(request.getAllocationMethod()),
                RevenueRecognitionMethod.valueOf(request.getRecognitionMethod()),
                request.getAccountId(),
                request.getCostCenterId(),
                request.getRuleConfig(),
                userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(rule, "Allocation rule created"));
    }

    /**
     * Updates an existing allocation rule by creating a new version.
     *
     * @param id        the rule identifier
     * @param request   the updated allocation rule details
     * @param principal the authenticated user
     * @return 200 with the new version of the allocation rule
     */
    @PutMapping("/rules/{id}")
    public ResponseEntity<ApiResponse<AllocationRule>> updateRule(
            @PathVariable String id,
            @Valid @RequestBody AllocationRuleRequest request,
            @AuthenticationPrincipal EventOpsUserDetails principal) {
        String userId = principal.getUser().getId();
        log.debug("PUT /api/finance/rules/{} – name={}, userId={}", id, request.getName(), userId);

        AllocationRule rule = financeService.updateRule(
                id,
                request.getName(),
                AllocationMethod.valueOf(request.getAllocationMethod()),
                RevenueRecognitionMethod.valueOf(request.getRecognitionMethod()),
                request.getAccountId(),
                request.getCostCenterId(),
                request.getRuleConfig(),
                userId);
        return ResponseEntity.ok(ApiResponse.success(rule, "Allocation rule updated"));
    }

    // ------------------------------------------------------------------
    // Postings
    // ------------------------------------------------------------------

    /**
     * Executes a financial posting with allocation engine integration.
     *
     * @param request   the posting details
     * @param principal the authenticated user
     * @return 201 with the completed posting journal
     */
    @PostMapping("/postings")
    public ResponseEntity<ApiResponse<PostingJournal>> executePosting(
            @Valid @RequestBody PostingRequest request,
            @AuthenticationPrincipal EventOpsUserDetails principal) {
        String userId = principal.getUser().getId();
        log.debug("POST /api/finance/postings – periodId={}, ruleId={}, amount={}, userId={}",
                request.getPeriodId(), request.getRuleId(), request.getTotalAmount(), userId);

        PostingJournal posting = financeService.executePosting(
                request.getPeriodId(),
                request.getSessionId(),
                request.getRuleId(),
                request.getTotalAmount(),
                request.getDescription(),
                userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(posting, "Posting executed"));
    }

    /**
     * Lists postings, optionally filtered by accounting period.
     *
     * @param periodId optional period ID filter
     * @return 200 with list of posting journals
     */
    @GetMapping("/postings")
    public ResponseEntity<ApiResponse<List<PostingJournal>>> listPostings(
            @RequestParam(required = false) String periodId) {
        log.debug("GET /api/finance/postings – periodId={}", periodId);

        List<PostingJournal> postings = financeService.listPostings(periodId);
        return ResponseEntity.ok(ApiResponse.success(postings));
    }

    /**
     * Returns the allocation line items for a posting.
     *
     * @param id the posting journal identifier
     * @return 200 with list of line items
     */
    @GetMapping("/postings/{id}/line-items")
    public ResponseEntity<ApiResponse<List<AllocationLineItem>>> getPostingLineItems(@PathVariable String id) {
        log.debug("GET /api/finance/postings/{}/line-items", id);

        List<AllocationLineItem> lineItems = financeService.getPostingLineItems(id);
        return ResponseEntity.ok(ApiResponse.success(lineItems));
    }

    /**
     * Reverses an existing posting.
     *
     * @param id        the posting journal identifier
     * @param principal the authenticated user
     * @return 200 with the reversed posting journal
     */
    @PostMapping("/postings/{id}/reverse")
    public ResponseEntity<ApiResponse<PostingJournal>> reversePosting(
            @PathVariable String id,
            @AuthenticationPrincipal EventOpsUserDetails principal) {
        String userId = principal.getUser().getId();
        log.debug("POST /api/finance/postings/{}/reverse – userId={}", id, userId);

        PostingJournal posting = financeService.reversePosting(id, userId);
        return ResponseEntity.ok(ApiResponse.success(posting, "Posting reversed"));
    }
}
