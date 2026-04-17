package com.eventops.service;

import com.eventops.audit.logging.AuditService;
import com.eventops.common.exception.BusinessException;
import com.eventops.domain.audit.AuditActionType;
import com.eventops.domain.audit.AuditEvent;
import com.eventops.domain.audit.ExportJob;
import com.eventops.domain.audit.ExportStatus;
import com.eventops.domain.audit.WatermarkPolicy;
import com.eventops.domain.checkin.CheckInRecord;
import com.eventops.domain.checkin.CheckInStatus;
import com.eventops.domain.finance.AllocationLineItem;
import com.eventops.domain.finance.AllocationRule;
import com.eventops.domain.finance.PostingJournal;
import com.eventops.domain.finance.PostingStatus;
import com.eventops.domain.registration.Registration;
import com.eventops.domain.registration.RegistrationStatus;
import com.eventops.domain.user.RoleType;
import com.eventops.domain.user.User;
import com.eventops.repository.audit.AuditEventRepository;
import com.eventops.repository.audit.ExportJobRepository;
import com.eventops.repository.audit.FieldDiffRepository;
import com.eventops.repository.audit.WatermarkPolicyRepository;
import com.eventops.repository.checkin.CheckInRecordRepository;
import com.eventops.repository.finance.AllocationLineItemRepository;
import com.eventops.repository.finance.AllocationRuleRepository;
import com.eventops.repository.finance.PostingJournalRepository;
import com.eventops.repository.registration.RegistrationRepository;
import com.eventops.repository.user.UserRepository;
import com.eventops.service.audit.ExportService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditExportServiceTest {

    @Mock ExportJobRepository exportJobRepository;
    @Mock WatermarkPolicyRepository watermarkPolicyRepository;
    @Mock AuditEventRepository auditEventRepository;
    @Mock FieldDiffRepository fieldDiffRepository;
    @Mock AuditService auditService;
    @Mock RegistrationRepository registrationRepository;
    @Mock CheckInRecordRepository checkInRecordRepository;
    @Mock UserRepository userRepository;
    @Mock PostingJournalRepository postingJournalRepository;
    @Mock AllocationLineItemRepository allocationLineItemRepository;
    @Mock AllocationRuleRepository allocationRuleRepository;

    @InjectMocks ExportService service;

    @TempDir
    Path tempDir;

    private static final String USER_ID = "user-1";
    private static final String OPERATOR_NAME = "Test Operator";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "storagePath", tempDir.toString());
    }

    // ------------------------------------------------------------------
    // Helper builders
    // ------------------------------------------------------------------

    private User buildUser(String id, RoleType role) {
        User u = new User();
        u.setId(id);
        u.setUsername("user_" + id);
        u.setDisplayName("Display " + id);
        u.setPasswordHash("hash");
        u.setRoleType(role);
        return u;
    }

    private AuditEvent buildAuditEvent(String id) {
        AuditEvent e = new AuditEvent();
        e.setId(id);
        e.setActionType(AuditActionType.LOGIN_SUCCESS);
        e.setOperatorId("op-1");
        e.setOperatorName("Op One");
        e.setEntityType("User");
        e.setEntityId("eid-1");
        e.setDescription("desc");
        // Manually set createdAt via reflection since @PrePersist won't fire
        try {
            java.lang.reflect.Field f = AuditEvent.class.getDeclaredField("createdAt");
            f.setAccessible(true);
            f.set(e, Instant.parse("2025-01-15T10:00:00Z"));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return e;
    }

    private WatermarkPolicy buildPolicy(String reportType, String roleType, boolean allowed) {
        WatermarkPolicy p = new WatermarkPolicy();
        p.setId("policy-1");
        p.setReportType(reportType);
        p.setRoleType(roleType);
        p.setDownloadAllowed(allowed);
        p.setWatermarkTemplate("template");
        p.setActive(true);
        return p;
    }

    private ExportJob savedJob() {
        // The service creates ExportJob via new then calls save; we just return the argument with an id set
        return null; // handled by answer callback
    }

    private void stubUserAsRole(String userId, RoleType role) {
        when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(userId, role)));
    }

    private void stubPolicyAllowed(String reportType, String roleType) {
        when(watermarkPolicyRepository.findByReportTypeAndRoleType(reportType, roleType))
                .thenReturn(List.of(buildPolicy(reportType, roleType, true)));
    }

    private void stubPolicyDenied(String reportType, String roleType) {
        when(watermarkPolicyRepository.findByReportTypeAndRoleType(reportType, roleType))
                .thenReturn(List.of(buildPolicy(reportType, roleType, false)));
    }

    private void stubPolicyNone(String reportType, String roleType) {
        when(watermarkPolicyRepository.findByReportTypeAndRoleType(reportType, roleType))
                .thenReturn(Collections.emptyList());
    }

    private void stubExportJobSave() {
        when(exportJobRepository.save(any(ExportJob.class))).thenAnswer(inv -> {
            ExportJob j = inv.getArgument(0);
            if (j.getId() == null) {
                j.setId("job-" + System.nanoTime());
            }
            return j;
        });
    }

    // ==================================================================
    // generateAuditExport
    // ==================================================================

    @Test
    void generateAuditExport_happyPath_nullFilter_returnsCompletedJob() {
        stubUserAsRole(USER_ID, RoleType.SYSTEM_ADMIN);
        stubPolicyAllowed("AUDIT_LOG", "SYSTEM_ADMIN");
        stubExportJobSave();

        AuditEvent event = buildAuditEvent("e1");
        when(auditEventRepository.findAll()).thenReturn(List.of(event));

        ExportJob result = service.generateAuditExport(USER_ID, OPERATOR_NAME, null);

        assertEquals(ExportStatus.COMPLETED, result.getStatus());
        assertEquals(1, result.getRecordCount());
        assertNotNull(result.getFilePath());
        assertTrue(Files.exists(Path.of(result.getFilePath())));
        verify(auditService).log(eq(AuditActionType.EXPORT_GENERATED),
                eq(USER_ID), eq(OPERATOR_NAME), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void generateAuditExport_happyPath_withDateRangeFilter() {
        stubUserAsRole(USER_ID, RoleType.SYSTEM_ADMIN);
        stubPolicyNone("AUDIT_LOG", "SYSTEM_ADMIN"); // no policy = allowed
        stubExportJobSave();

        AuditEvent event = buildAuditEvent("e2");
        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-12-31T23:59:59Z");
        when(auditEventRepository.findByCreatedAtBetween(from, to)).thenReturn(List.of(event));

        String filter = "{\"dateFrom\":\"2025-01-01T00:00:00Z\",\"dateTo\":\"2025-12-31T23:59:59Z\"}";
        ExportJob result = service.generateAuditExport(USER_ID, OPERATOR_NAME, filter);

        assertEquals(ExportStatus.COMPLETED, result.getStatus());
        assertEquals(1, result.getRecordCount());
    }

    @Test
    void generateAuditExport_happyPath_withActionTypeFilter() {
        stubUserAsRole(USER_ID, RoleType.SYSTEM_ADMIN);
        stubPolicyNone("AUDIT_LOG", "SYSTEM_ADMIN");
        stubExportJobSave();

        AuditEvent event = buildAuditEvent("e3");
        event.setActionType(AuditActionType.LOGIN_SUCCESS);
        when(auditEventRepository.findByActionType(AuditActionType.LOGIN_SUCCESS)).thenReturn(List.of(event));

        String filter = "{\"actionType\":\"LOGIN_SUCCESS\"}";
        ExportJob result = service.generateAuditExport(USER_ID, OPERATOR_NAME, filter);

        assertEquals(ExportStatus.COMPLETED, result.getStatus());
        assertEquals(1, result.getRecordCount());
    }

    @Test
    void generateAuditExport_withDateRangeAndActionType_filtersInMemory() {
        stubUserAsRole(USER_ID, RoleType.SYSTEM_ADMIN);
        stubPolicyNone("AUDIT_LOG", "SYSTEM_ADMIN");
        stubExportJobSave();

        AuditEvent e1 = buildAuditEvent("e1");
        e1.setActionType(AuditActionType.LOGIN_SUCCESS);
        AuditEvent e2 = buildAuditEvent("e2");
        e2.setActionType(AuditActionType.LOGOUT);

        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-12-31T23:59:59Z");
        when(auditEventRepository.findByCreatedAtBetween(from, to)).thenReturn(List.of(e1, e2));

        String filter = "{\"dateFrom\":\"2025-01-01T00:00:00Z\",\"dateTo\":\"2025-12-31T23:59:59Z\",\"actionType\":\"LOGIN_SUCCESS\"}";
        ExportJob result = service.generateAuditExport(USER_ID, OPERATOR_NAME, filter);

        assertEquals(ExportStatus.COMPLETED, result.getStatus());
        assertEquals(1, result.getRecordCount()); // only LOGIN_SUCCESS
    }

    @Test
    void generateAuditExport_badJsonFilter_returnsAllEvents() {
        stubUserAsRole(USER_ID, RoleType.SYSTEM_ADMIN);
        stubPolicyNone("AUDIT_LOG", "SYSTEM_ADMIN");
        stubExportJobSave();

        when(auditEventRepository.findAll()).thenReturn(List.of(buildAuditEvent("e1")));

        ExportJob result = service.generateAuditExport(USER_ID, OPERATOR_NAME, "not-valid-json{{{");

        assertEquals(ExportStatus.COMPLETED, result.getStatus());
        assertEquals(1, result.getRecordCount());
    }

    @Test
    void generateAuditExport_emptyStringFilter_returnsAllEvents() {
        stubUserAsRole(USER_ID, RoleType.SYSTEM_ADMIN);
        stubPolicyNone("AUDIT_LOG", "SYSTEM_ADMIN");
        stubExportJobSave();

        when(auditEventRepository.findAll()).thenReturn(Collections.emptyList());

        ExportJob result = service.generateAuditExport(USER_ID, OPERATOR_NAME, "  ");

        assertEquals(ExportStatus.COMPLETED, result.getStatus());
        assertEquals(0, result.getRecordCount());
    }

    @Test
    void generateAuditExport_deniedByPolicy_throws403() {
        stubUserAsRole(USER_ID, RoleType.SYSTEM_ADMIN);
        stubPolicyDenied("AUDIT_LOG", "SYSTEM_ADMIN");
        stubExportJobSave();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateAuditExport(USER_ID, OPERATOR_NAME, null));

        assertEquals(403, ex.getHttpStatus());
        assertEquals("EXPORT_DENIED", ex.getErrorCode());
        verify(auditService).log(eq(AuditActionType.EXPORT_DENIED),
                eq(USER_ID), eq(OPERATOR_NAME), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void generateAuditExport_nullFieldsOnEvent_handledGracefully() {
        stubUserAsRole(USER_ID, RoleType.SYSTEM_ADMIN);
        stubPolicyNone("AUDIT_LOG", "SYSTEM_ADMIN");
        stubExportJobSave();

        // Create an event with many null fields
        AuditEvent sparse = new AuditEvent();
        sparse.setId("sparse-1");
        // actionType, operatorName, entityType, entityId, description, createdAt all null
        when(auditEventRepository.findAll()).thenReturn(List.of(sparse));

        ExportJob result = service.generateAuditExport(USER_ID, OPERATOR_NAME, null);

        assertEquals(ExportStatus.COMPLETED, result.getStatus());
        assertEquals(1, result.getRecordCount());
    }

    @Test
    void generateAuditExport_filterWithNoRecognizedKeys_returnsAll() {
        stubUserAsRole(USER_ID, RoleType.SYSTEM_ADMIN);
        stubPolicyNone("AUDIT_LOG", "SYSTEM_ADMIN");
        stubExportJobSave();

        when(auditEventRepository.findAll()).thenReturn(Collections.emptyList());

        String filter = "{\"unknownKey\":\"value\"}";
        ExportJob result = service.generateAuditExport(USER_ID, OPERATOR_NAME, filter);

        assertEquals(ExportStatus.COMPLETED, result.getStatus());
    }

    // ==================================================================
    // generateRosterExport
    // ==================================================================

    @Test
    void generateRosterExport_happyPath_returnsCompletedJob() {
        stubUserAsRole(USER_ID, RoleType.EVENT_STAFF);
        stubPolicyAllowed("ROSTER", "EVENT_STAFF");
        stubExportJobSave();

        Registration reg = new Registration();
        reg.setId("reg-1");
        reg.setUserId("attendee-1");
        reg.setSessionId("session-1");
        reg.setStatus(RegistrationStatus.CONFIRMED);

        // Stub for each RegistrationStatus value
        for (RegistrationStatus status : RegistrationStatus.values()) {
            if (status == RegistrationStatus.CONFIRMED) {
                when(registrationRepository.findBySessionIdAndStatus("session-1", status)).thenReturn(List.of(reg));
            } else {
                when(registrationRepository.findBySessionIdAndStatus("session-1", status)).thenReturn(Collections.emptyList());
            }
        }

        CheckInRecord checkIn = new CheckInRecord();
        checkIn.setId("ci-1");
        checkIn.setUserId("attendee-1");
        checkIn.setSessionId("session-1");
        checkIn.setStatus(CheckInStatus.CHECKED_IN);
        checkIn.setCheckedInAt(Instant.parse("2025-06-01T09:00:00Z"));
        when(checkInRecordRepository.findBySessionId("session-1")).thenReturn(List.of(checkIn));

        User attendee = buildUser("attendee-1", RoleType.ATTENDEE);
        when(userRepository.findById("attendee-1")).thenReturn(Optional.of(attendee));

        ExportJob result = service.generateRosterExport("session-1", USER_ID, OPERATOR_NAME);

        assertEquals(ExportStatus.COMPLETED, result.getStatus());
        assertEquals(1, result.getRecordCount());
        assertNotNull(result.getFilePath());
        assertTrue(Files.exists(Path.of(result.getFilePath())));
    }

    @Test
    void generateRosterExport_withNullUserAndNoCheckIn() {
        stubUserAsRole(USER_ID, RoleType.EVENT_STAFF);
        stubPolicyNone("ROSTER", "EVENT_STAFF");
        stubExportJobSave();

        Registration reg = new Registration();
        reg.setId("reg-2");
        reg.setUserId("missing-user");
        reg.setSessionId("session-2");
        reg.setStatus(RegistrationStatus.WAITLISTED);

        for (RegistrationStatus status : RegistrationStatus.values()) {
            if (status == RegistrationStatus.WAITLISTED) {
                when(registrationRepository.findBySessionIdAndStatus("session-2", status)).thenReturn(List.of(reg));
            } else {
                when(registrationRepository.findBySessionIdAndStatus("session-2", status)).thenReturn(Collections.emptyList());
            }
        }

        when(checkInRecordRepository.findBySessionId("session-2")).thenReturn(Collections.emptyList());
        when(userRepository.findById("missing-user")).thenReturn(Optional.empty());

        ExportJob result = service.generateRosterExport("session-2", USER_ID, OPERATOR_NAME);

        assertEquals(ExportStatus.COMPLETED, result.getStatus());
        assertEquals(1, result.getRecordCount());
    }

    @Test
    void generateRosterExport_deniedByPolicy_throws403() {
        stubUserAsRole(USER_ID, RoleType.EVENT_STAFF);
        stubPolicyDenied("ROSTER", "EVENT_STAFF");
        stubExportJobSave();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateRosterExport("session-1", USER_ID, OPERATOR_NAME));

        assertEquals(403, ex.getHttpStatus());
        assertEquals("EXPORT_DENIED", ex.getErrorCode());
    }

    // ==================================================================
    // generateFinanceReport
    // ==================================================================

    @Test
    void generateFinanceReport_happyPath_returnsCompletedJob() {
        stubUserAsRole(USER_ID, RoleType.FINANCE_MANAGER);
        stubPolicyAllowed("FINANCE_REPORT", "FINANCE_MANAGER");
        stubExportJobSave();

        PostingJournal posting = new PostingJournal();
        posting.setId("pj-1");
        posting.setPeriodId("period-1");
        posting.setRuleId("rule-1");
        posting.setTotalAmount(new BigDecimal("1500.00"));
        posting.setStatus(PostingStatus.POSTED);
        posting.setPostedBy("admin-1");
        posting.setPostedAt(Instant.parse("2025-03-01T12:00:00Z"));

        when(postingJournalRepository.findByPeriodId("period-1")).thenReturn(List.of(posting));

        AllocationRule rule = new AllocationRule();
        rule.setId("rule-1");
        rule.setName("Standard Revenue");
        when(allocationRuleRepository.findAll()).thenReturn(List.of(rule));

        AllocationLineItem li = new AllocationLineItem();
        li.setId("li-1");
        li.setPostingId("pj-1");
        li.setAccountId("acct-100");
        li.setAmount(new BigDecimal("1500.00"));
        when(allocationLineItemRepository.findByPostingId("pj-1")).thenReturn(List.of(li));

        ExportJob result = service.generateFinanceReport("period-1", USER_ID, OPERATOR_NAME);

        assertEquals(ExportStatus.COMPLETED, result.getStatus());
        assertEquals(1, result.getRecordCount());
        assertNotNull(result.getFilePath());
        assertTrue(Files.exists(Path.of(result.getFilePath())));
    }

    @Test
    void generateFinanceReport_nullFieldsOnPosting_handledGracefully() {
        stubUserAsRole(USER_ID, RoleType.FINANCE_MANAGER);
        stubPolicyNone("FINANCE_REPORT", "FINANCE_MANAGER");
        stubExportJobSave();

        PostingJournal posting = new PostingJournal();
        posting.setId("pj-2");
        posting.setPeriodId("period-2");
        posting.setRuleId("unknown-rule");
        // totalAmount, status, postedBy, postedAt are null
        when(postingJournalRepository.findByPeriodId("period-2")).thenReturn(List.of(posting));
        when(allocationRuleRepository.findAll()).thenReturn(Collections.emptyList()); // rule not in cache
        when(allocationLineItemRepository.findByPostingId("pj-2")).thenReturn(Collections.emptyList());

        ExportJob result = service.generateFinanceReport("period-2", USER_ID, OPERATOR_NAME);

        assertEquals(ExportStatus.COMPLETED, result.getStatus());
        assertEquals(1, result.getRecordCount());
    }

    @Test
    void generateFinanceReport_deniedByPolicy_throws403() {
        stubUserAsRole(USER_ID, RoleType.FINANCE_MANAGER);
        stubPolicyDenied("FINANCE_REPORT", "FINANCE_MANAGER");
        stubExportJobSave();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.generateFinanceReport("period-1", USER_ID, OPERATOR_NAME));

        assertEquals(403, ex.getHttpStatus());
        assertEquals("EXPORT_DENIED", ex.getErrorCode());
    }

    // ==================================================================
    // downloadExport
    // ==================================================================

    @Test
    void downloadExport_happyPath_returnsFileBytes() throws Exception {
        // Write a file for the download to read
        Path file = tempDir.resolve("test_export.csv");
        Files.writeString(file, "header\nrow1");

        ExportJob job = new ExportJob();
        job.setId("job-dl-1");
        job.setExportType("AUDIT_LOG");
        job.setRequestedBy(USER_ID);
        job.setStatus(ExportStatus.COMPLETED);
        job.setFilePath(file.toString());

        when(exportJobRepository.findById("job-dl-1")).thenReturn(Optional.of(job));
        stubUserAsRole(USER_ID, RoleType.SYSTEM_ADMIN);
        stubPolicyAllowed("AUDIT_LOG", "SYSTEM_ADMIN");

        byte[] result = service.downloadExport("job-dl-1", USER_ID);

        assertNotNull(result);
        assertTrue(result.length > 0);
        assertEquals("header\nrow1", new String(result));
        verify(auditService).log(eq(AuditActionType.EXPORT_GENERATED),
                eq(USER_ID), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void downloadExport_notFound_throws404() {
        when(exportJobRepository.findById("missing")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.downloadExport("missing", USER_ID));

        assertEquals(404, ex.getHttpStatus());
        assertEquals("NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void downloadExport_nonOwnerNonAdmin_throws403() {
        ExportJob job = new ExportJob();
        job.setId("job-other");
        job.setExportType("AUDIT_LOG");
        job.setRequestedBy("other-user");
        job.setStatus(ExportStatus.COMPLETED);

        when(exportJobRepository.findById("job-other")).thenReturn(Optional.of(job));
        stubUserAsRole(USER_ID, RoleType.EVENT_STAFF); // not SYSTEM_ADMIN and not the owner

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.downloadExport("job-other", USER_ID));

        assertEquals(403, ex.getHttpStatus());
        assertEquals("EXPORT_SCOPE_DENIED", ex.getErrorCode());
    }

    @Test
    void downloadExport_systemAdmin_canAccessOtherUsersExport() throws Exception {
        Path file = tempDir.resolve("admin_dl.csv");
        Files.writeString(file, "data");

        ExportJob job = new ExportJob();
        job.setId("job-admin");
        job.setExportType("AUDIT_LOG");
        job.setRequestedBy("other-user"); // different owner
        job.setStatus(ExportStatus.COMPLETED);
        job.setFilePath(file.toString());

        when(exportJobRepository.findById("job-admin")).thenReturn(Optional.of(job));
        stubUserAsRole(USER_ID, RoleType.SYSTEM_ADMIN);
        stubPolicyAllowed("AUDIT_LOG", "SYSTEM_ADMIN");

        byte[] result = service.downloadExport("job-admin", USER_ID);

        assertNotNull(result);
        assertEquals("data", new String(result));
    }

    @Test
    void downloadExport_roleNotAllowed_throws403() {
        ExportJob job = new ExportJob();
        job.setId("job-role");
        job.setExportType("AUDIT_LOG");
        job.setRequestedBy(USER_ID);
        job.setStatus(ExportStatus.COMPLETED);

        when(exportJobRepository.findById("job-role")).thenReturn(Optional.of(job));
        // EVENT_STAFF is NOT allowed for AUDIT_LOG exports
        stubUserAsRole(USER_ID, RoleType.EVENT_STAFF);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.downloadExport("job-role", USER_ID));

        assertEquals(403, ex.getHttpStatus());
        assertEquals("EXPORT_ROLE_DENIED", ex.getErrorCode());
    }

    @Test
    void downloadExport_notCompleted_throws422() {
        ExportJob job = new ExportJob();
        job.setId("job-pending");
        job.setExportType("AUDIT_LOG");
        job.setRequestedBy(USER_ID);
        job.setStatus(ExportStatus.PENDING);

        when(exportJobRepository.findById("job-pending")).thenReturn(Optional.of(job));
        stubUserAsRole(USER_ID, RoleType.SYSTEM_ADMIN);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.downloadExport("job-pending", USER_ID));

        assertEquals(422, ex.getHttpStatus());
        assertEquals("EXPORT_NOT_READY", ex.getErrorCode());
    }

    @Test
    void downloadExport_policyReDenied_throws403() {
        Path file = tempDir.resolve("re_denied.csv");

        ExportJob job = new ExportJob();
        job.setId("job-re-denied");
        job.setExportType("AUDIT_LOG");
        job.setRequestedBy(USER_ID);
        job.setStatus(ExportStatus.COMPLETED);
        job.setFilePath(file.toString());

        when(exportJobRepository.findById("job-re-denied")).thenReturn(Optional.of(job));
        stubUserAsRole(USER_ID, RoleType.SYSTEM_ADMIN);
        // Policy now denies at download time
        stubPolicyDenied("AUDIT_LOG", "SYSTEM_ADMIN");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.downloadExport("job-re-denied", USER_ID));

        assertEquals(403, ex.getHttpStatus());
        assertEquals("EXPORT_DENIED", ex.getErrorCode());
    }

    @Test
    void downloadExport_fileNotReadable_throws500() {
        ExportJob job = new ExportJob();
        job.setId("job-io");
        job.setExportType("AUDIT_LOG");
        job.setRequestedBy(USER_ID);
        job.setStatus(ExportStatus.COMPLETED);
        job.setFilePath(tempDir.resolve("nonexistent_file.csv").toString());

        when(exportJobRepository.findById("job-io")).thenReturn(Optional.of(job));
        stubUserAsRole(USER_ID, RoleType.SYSTEM_ADMIN);
        stubPolicyAllowed("AUDIT_LOG", "SYSTEM_ADMIN");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.downloadExport("job-io", USER_ID));

        assertEquals(500, ex.getHttpStatus());
        assertEquals("EXPORT_READ_FAILED", ex.getErrorCode());
    }

    // ==================================================================
    // updatePolicy
    // ==================================================================

    @Test
    void updatePolicy_happyPath_updatesAndReturns() {
        WatermarkPolicy existing = buildPolicy("AUDIT_LOG", "SYSTEM_ADMIN", false);
        when(watermarkPolicyRepository.findById("policy-1")).thenReturn(Optional.of(existing));
        when(watermarkPolicyRepository.save(any(WatermarkPolicy.class))).thenAnswer(inv -> inv.getArgument(0));

        WatermarkPolicy result = service.updatePolicy("policy-1", true, "New Template");

        assertTrue(result.isDownloadAllowed());
        assertEquals("New Template", result.getWatermarkTemplate());
        verify(watermarkPolicyRepository).save(existing);
    }

    @Test
    void updatePolicy_notFound_throws404() {
        when(watermarkPolicyRepository.findById("missing")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updatePolicy("missing", true, "tmpl"));

        assertEquals(404, ex.getHttpStatus());
        assertEquals("NOT_FOUND", ex.getErrorCode());
    }

    // ==================================================================
    // getExportPolicies
    // ==================================================================

    @Test
    void getExportPolicies_returnsList() {
        WatermarkPolicy p1 = buildPolicy("AUDIT_LOG", "SYSTEM_ADMIN", true);
        WatermarkPolicy p2 = buildPolicy("ROSTER", "EVENT_STAFF", true);
        p2.setId("policy-2");
        when(watermarkPolicyRepository.findByActiveTrue()).thenReturn(List.of(p1, p2));

        List<WatermarkPolicy> result = service.getExportPolicies();

        assertEquals(2, result.size());
    }

    // ==================================================================
    // resolveRoleType edge case (unknown user)
    // ==================================================================

    @Test
    void generateAuditExport_unknownUser_policyCheckedWithUnknownRole() {
        // User not found -> resolveRoleType returns "UNKNOWN"
        when(userRepository.findById("ghost")).thenReturn(Optional.empty());
        // No policy for UNKNOWN -> default allowed
        when(watermarkPolicyRepository.findByReportTypeAndRoleType("AUDIT_LOG", "UNKNOWN"))
                .thenReturn(Collections.emptyList());
        stubExportJobSave();

        when(auditEventRepository.findAll()).thenReturn(Collections.emptyList());

        ExportJob result = service.generateAuditExport("ghost", OPERATOR_NAME, null);

        assertEquals(ExportStatus.COMPLETED, result.getStatus());
    }
}
