package com.eventops;

import com.eventops.domain.event.EventSession;
import com.eventops.domain.event.SessionStatus;
import com.eventops.domain.finance.Account;
import com.eventops.domain.finance.AccountingPeriod;
import com.eventops.domain.finance.AllocationMethod;
import com.eventops.domain.finance.AllocationRule;
import com.eventops.domain.finance.CostCenter;
import com.eventops.domain.finance.PeriodStatus;
import com.eventops.domain.finance.RevenueRecognitionMethod;
import com.eventops.domain.user.RoleType;
import com.eventops.repository.event.EventSessionRepository;
import com.eventops.repository.finance.AccountRepository;
import com.eventops.repository.finance.AccountingPeriodRepository;
import com.eventops.repository.finance.AllocationLineItemRepository;
import com.eventops.repository.finance.AllocationRuleRepository;
import com.eventops.repository.finance.CostCenterRepository;
import com.eventops.repository.finance.PostingJournalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FinanceControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountingPeriodRepository accountingPeriodRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private CostCenterRepository costCenterRepository;
    @Autowired private AllocationRuleRepository allocationRuleRepository;
    @Autowired private PostingJournalRepository postingJournalRepository;
    @Autowired private AllocationLineItemRepository allocationLineItemRepository;
    @Autowired private EventSessionRepository eventSessionRepository;

    @BeforeEach
    void resetData() {
        allocationLineItemRepository.deleteAll();
        postingJournalRepository.deleteAll();
        allocationRuleRepository.deleteAll();
        costCenterRepository.deleteAll();
        accountRepository.deleteAll();
        accountingPeriodRepository.deleteAll();
        eventSessionRepository.deleteAll();
    }

    @Test
    void listPeriods_returnsPeriods() throws Exception {
        seedPeriod("period-1", "FY26-Q1", PeriodStatus.OPEN);

        mockMvc.perform(get("/api/finance/periods")
                        .with(TestSecurity.user("finance-1", "finance", RoleType.FINANCE_MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createPeriod_returnsCreatedPeriod() throws Exception {
        mockMvc.perform(post("/api/finance/periods")
                        .with(csrf())
                        .with(TestSecurity.user("finance-1", "finance", RoleType.FINANCE_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "FY26-Q2",
                                  "startDate": "2026-04-01",
                                  "endDate": "2026-06-30"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Accounting period created"));
    }

    @Test
    void closePeriod_returnsClosedPeriod() throws Exception {
        AccountingPeriod period = seedPeriod("period-1", "FY26-Q1", PeriodStatus.OPEN);

        mockMvc.perform(put("/api/finance/periods/" + period.getId() + "/close")
                        .with(csrf())
                        .with(TestSecurity.user("finance-1", "finance", RoleType.FINANCE_MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Accounting period closed"));
    }

    @Test
    void listAccounts_returnsAccounts() throws Exception {
        seedAccount("acct-1", "4000", "Deferred Revenue", "LIABILITY");

        mockMvc.perform(get("/api/finance/accounts")
                        .with(TestSecurity.user("finance-1", "finance", RoleType.FINANCE_MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createAccount_returnsCreatedAccount() throws Exception {
        mockMvc.perform(post("/api/finance/accounts")
                        .with(csrf())
                        .with(TestSecurity.user("finance-1", "finance", RoleType.FINANCE_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountCode": "4000",
                                  "name": "Deferred Revenue",
                                  "description": "Revenue account",
                                  "accountType": "LIABILITY"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Account created"));
    }

    @Test
    void listCostCenters_returnsCenters() throws Exception {
        seedCostCenter("cc-1", "OPS", "Operations", "DEPARTMENT");

        mockMvc.perform(get("/api/finance/cost-centers")
                        .with(TestSecurity.user("finance-1", "finance", RoleType.FINANCE_MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createCostCenter_returnsCreatedCenter() throws Exception {
        mockMvc.perform(post("/api/finance/cost-centers")
                        .with(csrf())
                        .with(TestSecurity.user("finance-1", "finance", RoleType.FINANCE_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "OPS",
                                  "name": "Operations",
                                  "description": "Ops team",
                                  "centerType": "DEPARTMENT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cost center created"));
    }

    @Test
    void listRules_returnsRules() throws Exception {
        Account account = seedAccount("acct-1", "5000", "Revenue", "REVENUE");
        CostCenter center = seedCostCenter("cc-1", "ENG", "Engineering", "DEPARTMENT");
        seedRule("rule-1", "Test Rule", account.getId(), center.getId());

        mockMvc.perform(get("/api/finance/rules")
                        .with(TestSecurity.user("finance-1", "finance", RoleType.FINANCE_MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createRule_returnsCreatedRule() throws Exception {
        Account account = seedAccount("acct-1", "5000", "Revenue", "REVENUE");
        CostCenter center = seedCostCenter("cc-1", "ENG", "Engineering", "DEPARTMENT");

        mockMvc.perform(post("/api/finance/rules")
                        .with(csrf())
                        .with(TestSecurity.user("finance-1", "finance", RoleType.FINANCE_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Facility Allocation",
                                  "allocationMethod": "FIXED",
                                  "recognitionMethod": "IMMEDIATE",
                                  "accountId": "%s",
                                  "costCenterId": "%s",
                                  "ruleConfig": "{\\\"%s\\\": 100.00}"
                                }
                                """.formatted(account.getId(), center.getId(), center.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Allocation rule created"));
    }

    @Test
    void updateRule_returnsUpdatedRule() throws Exception {
        Account account = seedAccount("acct-1", "5000", "Revenue", "REVENUE");
        CostCenter center = seedCostCenter("cc-1", "ENG", "Engineering", "DEPARTMENT");
        AllocationRule rule = seedRule("rule-1", "Original Rule", account.getId(), center.getId());

        mockMvc.perform(put("/api/finance/rules/" + rule.getId())
                        .with(csrf())
                        .with(TestSecurity.user("finance-1", "finance", RoleType.FINANCE_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Updated Rule",
                                  "allocationMethod": "PROPORTIONAL",
                                  "recognitionMethod": "OVER_SESSION_DATES",
                                  "accountId": "%s",
                                  "costCenterId": "%s",
                                  "ruleConfig": "basis=registrations"
                                }
                                """.formatted(account.getId(), center.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Allocation rule updated"));
    }

    @Test
    void executePosting_returnsCreatedPosting() throws Exception {
        AccountingPeriod period = seedPeriod("period-1", "FY26-Q2", PeriodStatus.OPEN);
        Account account = seedAccount("acct-1", "5000", "Revenue", "REVENUE");
        CostCenter center = seedCostCenter("cc-1", "ENG", "Engineering", "DEPARTMENT");
        AllocationRule rule = seedRule("rule-1", "Posting Rule", account.getId(), center.getId());
        seedEventSession("session-1");

        mockMvc.perform(post("/api/finance/postings")
                        .with(csrf())
                        .with(TestSecurity.user("finance-1", "finance", RoleType.FINANCE_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "periodId": "%s",
                                  "sessionId": "session-1",
                                  "ruleId": "%s",
                                  "totalAmount": 250.00,
                                  "description": "April posting"
                                }
                                """.formatted(period.getId(), rule.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Posting executed"));
    }

    @Test
    void listPostings_returnsPostings() throws Exception {
        AccountingPeriod period = seedPeriod("period-1", "FY26-Q2", PeriodStatus.OPEN);

        mockMvc.perform(get("/api/finance/postings")
                        .param("periodId", period.getId())
                        .with(TestSecurity.user("finance-1", "finance", RoleType.FINANCE_MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void reversePosting_returnsReversedPosting() throws Exception {
        // Seed and execute a posting first via the API to get a real posting ID
        AccountingPeriod period = seedPeriod("period-1", "FY26-Q2", PeriodStatus.OPEN);
        Account account = seedAccount("acct-1", "5000", "Revenue", "REVENUE");
        CostCenter center = seedCostCenter("cc-1", "ENG", "Engineering", "DEPARTMENT");
        AllocationRule rule = seedRule("rule-1", "Posting Rule", account.getId(), center.getId());
        seedEventSession("session-1");

        // Execute a posting to get a real posting ID
        String postingResponse = mockMvc.perform(post("/api/finance/postings")
                        .with(csrf())
                        .with(TestSecurity.user("finance-1", "finance", RoleType.FINANCE_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "periodId": "%s",
                                  "sessionId": "session-1",
                                  "ruleId": "%s",
                                  "totalAmount": 100.00,
                                  "description": "Test posting"
                                }
                                """.formatted(period.getId(), rule.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Extract posting ID from the response
        String postingId = com.jayway.jsonpath.JsonPath.read(postingResponse, "$.data.id");

        mockMvc.perform(post("/api/finance/postings/" + postingId + "/reverse")
                        .with(csrf())
                        .with(TestSecurity.user("finance-1", "finance", RoleType.FINANCE_MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Posting reversed"));
    }

    @Test
    void getPostingLineItems_returnsLineItems() throws Exception {
        // Seed and execute a posting first
        AccountingPeriod period = seedPeriod("period-li", "FY26-LI", PeriodStatus.OPEN);
        Account account = seedAccount("acct-li", "6000", "LI Revenue", "REVENUE");
        CostCenter center = seedCostCenter("cc-li", "LI-CC", "LI Center", "DEPARTMENT");
        AllocationRule rule = seedRule("rule-li", "LI Rule", account.getId(), center.getId());
        seedEventSession("session-li");

        String postingResponse = mockMvc.perform(post("/api/finance/postings")
                        .with(csrf())
                        .with(TestSecurity.user("finance-1", "finance", RoleType.FINANCE_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "periodId": "%s",
                                  "sessionId": "session-li",
                                  "ruleId": "%s",
                                  "totalAmount": 500.00,
                                  "description": "Line items test"
                                }
                                """.formatted(period.getId(), rule.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String postingId = com.jayway.jsonpath.JsonPath.read(postingResponse, "$.data.id");

        mockMvc.perform(get("/api/finance/postings/" + postingId + "/line-items")
                        .with(TestSecurity.user("finance-1", "finance", RoleType.FINANCE_MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    // ---- seed helpers ----

    private AccountingPeriod seedPeriod(String id, String name, PeriodStatus status) {
        AccountingPeriod period = new AccountingPeriod();
        period.setId(id);
        period.setName(name);
        period.setStartDate(LocalDate.of(2026, 4, 1));
        period.setEndDate(LocalDate.of(2026, 6, 30));
        period.setStatus(status);
        return accountingPeriodRepository.save(period);
    }

    private Account seedAccount(String id, String code, String name, String type) {
        Account account = new Account();
        account.setId(id);
        account.setAccountCode(code);
        account.setName(name);
        account.setDescription("Test account");
        account.setAccountType(type);
        account.setActive(true);
        return accountRepository.save(account);
    }

    private CostCenter seedCostCenter(String id, String code, String name, String centerType) {
        CostCenter center = new CostCenter();
        center.setId(id);
        center.setCode(code);
        center.setName(name);
        center.setDescription("Test cost center");
        center.setCenterType(centerType);
        center.setActive(true);
        return costCenterRepository.save(center);
    }

    private AllocationRule seedRule(String id, String name, String accountId, String costCenterId) {
        AllocationRule rule = new AllocationRule();
        rule.setId(id);
        rule.setName(name);
        rule.setAllocationMethod(AllocationMethod.FIXED);
        rule.setRecognitionMethod(RevenueRecognitionMethod.IMMEDIATE);
        rule.setAccountId(accountId);
        rule.setCostCenterId(costCenterId);
        rule.setRuleConfig("{\"" + costCenterId + "\": 250.00}");
        rule.setVersion(1);
        rule.setActive(true);
        rule.setCreatedBy("finance-1");
        return allocationRuleRepository.save(rule);
    }

    private void seedEventSession(String id) {
        LocalDateTime start = LocalDateTime.of(2026, 4, 20, 9, 0);
        EventSession session = new EventSession();
        session.setId(id);
        session.setTitle("Test Session");
        session.setDescription("Test session for posting");
        session.setLocation("Room A");
        session.setStartTime(start);
        session.setEndTime(start.plusHours(1));
        session.setMaxCapacity(100);
        session.setCurrentRegistrations(0);
        session.setStatus(SessionStatus.OPEN_FOR_REGISTRATION);
        session.setCheckinWindowBeforeMinutes(30);
        session.setCheckinWindowAfterMinutes(15);
        session.setDeviceBindingRequired(false);
        eventSessionRepository.save(session);
    }
}
