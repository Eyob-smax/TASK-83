package com.eventops.service.audit;

import com.eventops.audit.logging.AuditService;
import com.eventops.common.exception.BusinessException;
import com.eventops.domain.audit.AuditActionType;
import com.eventops.domain.audit.AuditEvent;
import com.eventops.domain.audit.ExportJob;
import com.eventops.domain.audit.ExportStatus;
import com.eventops.domain.audit.FieldDiff;
import com.eventops.domain.audit.WatermarkPolicy;
import com.eventops.domain.checkin.CheckInRecord;
import com.eventops.domain.checkin.CheckInStatus;
import com.eventops.domain.finance.AllocationLineItem;
import com.eventops.domain.finance.AllocationRule;
import com.eventops.domain.finance.PostingJournal;
import com.eventops.domain.registration.Registration;
import com.eventops.domain.user.User;
import com.eventops.integration.export.CsvGenerator;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generates watermarked CSV exports for audit logs, session rosters, and
 * finance reports. Each export is tracked as an {@link ExportJob} and
 * governed by {@link WatermarkPolicy} rules that control download
 * permissions per report type and user role.
 *
 * <p>The generated files are written to the local storage path configured
 * via {@code eventops.export.storage-path}.</p>
 */
@Service("auditExportService")
@Transactional
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneOffset.UTC);

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ISO_INSTANT;

    private static final String ROLE_SYSTEM_ADMIN = "SYSTEM_ADMIN";
    private static final String ROLE_EVENT_STAFF = "EVENT_STAFF";
    private static final String ROLE_FINANCE_MANAGER = "FINANCE_MANAGER";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ExportJobRepository exportJobRepository;
    private final WatermarkPolicyRepository watermarkPolicyRepository;
    private final AuditEventRepository auditEventRepository;
    private final FieldDiffRepository fieldDiffRepository;
    private final AuditService auditService;
    private final RegistrationRepository registrationRepository;
    private final CheckInRecordRepository checkInRecordRepository;
    private final UserRepository userRepository;
    private final PostingJournalRepository postingJournalRepository;
    private final AllocationLineItemRepository allocationLineItemRepository;
    private final AllocationRuleRepository allocationRuleRepository;

    @Value("${eventops.export.storage-path}")
    private String storagePath;

    public ExportService(ExportJobRepository exportJobRepository,
                         WatermarkPolicyRepository watermarkPolicyRepository,
                         AuditEventRepository auditEventRepository,
                         FieldDiffRepository fieldDiffRepository,
                         AuditService auditService,
                         RegistrationRepository registrationRepository,
                         CheckInRecordRepository checkInRecordRepository,
                         UserRepository userRepository,
                         PostingJournalRepository postingJournalRepository,
                         AllocationLineItemRepository allocationLineItemRepository,
                         AllocationRuleRepository allocationRuleRepository) {
        this.exportJobRepository = exportJobRepository;
        this.watermarkPolicyRepository = watermarkPolicyRepository;
        this.auditEventRepository = auditEventRepository;
        this.fieldDiffRepository = fieldDiffRepository;
        this.auditService = auditService;
        this.registrationRepository = registrationRepository;
        this.checkInRecordRepository = checkInRecordRepository;
        this.userRepository = userRepository;
        this.postingJournalRepository = postingJournalRepository;
        this.allocationLineItemRepository = allocationLineItemRepository;
        this.allocationRuleRepository = allocationRuleRepository;
    }

    // ------------------------------------------------------------------
    // Audit Log Export
    // ------------------------------------------------------------------

    /**
     * Generates a watermarked CSV export of audit log entries.
     *
     * <p>The export respects {@link WatermarkPolicy} rules: if the policy
     * for {@code AUDIT_LOG} and the requesting user's role disallows
     * downloads, the export job is created with status {@code DENIED} and
     * a {@link BusinessException} is thrown.</p>
     *
     * @param requestedBy    the ID of the user requesting the export
     * @param operatorName   the display name of the requesting operator
     * @param filterCriteria JSON string with optional keys: {@code dateFrom},
     *                       {@code dateTo}, {@code actionType}
     * @return the completed {@link ExportJob} with file metadata
     * @throws BusinessException if download is denied by watermark policy
     */
    public ExportJob generateAuditExport(String requestedBy, String operatorName, String filterCriteria) {
        // Check watermark policy
        if (!isDownloadAllowed("AUDIT_LOG", resolveRoleType(requestedBy))) {
            ExportJob denied = createExportJob("AUDIT_LOG", requestedBy, filterCriteria, ExportStatus.DENIED);
            exportJobRepository.save(denied);

            auditService.log(AuditActionType.EXPORT_DENIED,
                    requestedBy, operatorName, "SYSTEM",
                    "ExportJob", denied.getId(),
                    "Audit log export denied for operator: " + operatorName);

            throw new BusinessException("Export download is not permitted for your role", 403, "EXPORT_DENIED");
        }

        // Create export job in PENDING state
        ExportJob job = createExportJob("AUDIT_LOG", requestedBy, filterCriteria, ExportStatus.PENDING);
        job = exportJobRepository.save(job);

        // Transition to GENERATING
        job.setStatus(ExportStatus.GENERATING);
        job = exportJobRepository.save(job);

        try {
            // Query audit events based on filter criteria
            List<AuditEvent> events = queryAuditEvents(filterCriteria);

            // Build CSV content
            String watermarkHeader = buildWatermarkHeader(operatorName, "Audit Log Export");
            String[] headers = {"ID", "Timestamp", "Action", "Operator", "Entity Type", "Entity ID", "Description"};

            List<String[]> rows = new ArrayList<>();
            for (AuditEvent event : events) {
                rows.add(new String[]{
                        event.getId(),
                        event.getCreatedAt() != null ? ISO_FORMATTER.format(event.getCreatedAt()) : "",
                        event.getActionType() != null ? event.getActionType().name() : "",
                        event.getOperatorName() != null ? event.getOperatorName() : "",
                        event.getEntityType() != null ? event.getEntityType() : "",
                        event.getEntityId() != null ? event.getEntityId() : "",
                        event.getDescription() != null ? event.getDescription() : ""
                });
            }

            // Write CSV to file
            String csvContent = renderCsv(watermarkHeader, headers, rows);
            String fileName = "audit_export_" + TIMESTAMP_FORMATTER.format(Instant.now()) + ".csv";
            Path filePath = writeExportFile(fileName, csvContent);

            // Update job with completion details
            job.setStatus(ExportStatus.COMPLETED);
            job.setFilePath(filePath.toString());
            job.setFileSizeBytes(Files.size(filePath));
            job.setRecordCount(events.size());
            job.setWatermarkText(watermarkHeader);
            job.setCompletedAt(Instant.now());
            job = exportJobRepository.save(job);

            auditService.log(AuditActionType.EXPORT_GENERATED,
                    requestedBy, operatorName, "SYSTEM",
                    "ExportJob", job.getId(),
                    "Audit log export generated: " + events.size() + " records, file=" + fileName);

            log.info("Audit export completed: jobId={}, records={}, file={}",
                    job.getId(), events.size(), fileName);

            return job;

        } catch (IOException e) {
            job.setStatus(ExportStatus.FAILED);
            job.setErrorMessage("Failed to write export file: " + e.getMessage());
            exportJobRepository.save(job);
            log.error("Audit export failed: jobId={}", job.getId(), e);
            throw new BusinessException("Export file generation failed", 500, "EXPORT_FAILED");
        }
    }

    // ------------------------------------------------------------------
    // Roster Export
    // ------------------------------------------------------------------

    /**
     * Generates a watermarked CSV export of the attendee roster for a given
     * event session. Each row includes registration status and check-in
     * information.
     *
     * @param sessionId    the event session ID
     * @param requestedBy  the ID of the user requesting the export
     * @param operatorName the display name of the requesting operator
     * @return the completed {@link ExportJob} with file metadata
     * @throws BusinessException if download is denied by watermark policy
     */
    public ExportJob generateRosterExport(String sessionId, String requestedBy, String operatorName) {
        // Check watermark policy
        if (!isDownloadAllowed("ROSTER", resolveRoleType(requestedBy))) {
            ExportJob denied = createExportJob("ROSTER", requestedBy, null, ExportStatus.DENIED);
            exportJobRepository.save(denied);

            auditService.log(AuditActionType.EXPORT_DENIED,
                    requestedBy, operatorName, "SYSTEM",
                    "ExportJob", denied.getId(),
                    "Roster export denied for operator: " + operatorName);

            throw new BusinessException("Export download is not permitted for your role", 403, "EXPORT_DENIED");
        }

        // Create export job
        ExportJob job = createExportJob("ROSTER", requestedBy, "{\"sessionId\":\"" + sessionId + "\"}", ExportStatus.PENDING);
        job = exportJobRepository.save(job);

        job.setStatus(ExportStatus.GENERATING);
        job = exportJobRepository.save(job);

        try {
            // Load registrations for the session (all statuses)
            List<Registration> registrations = new ArrayList<>();
            for (com.eventops.domain.registration.RegistrationStatus status :
                    com.eventops.domain.registration.RegistrationStatus.values()) {
                registrations.addAll(registrationRepository.findBySessionIdAndStatus(sessionId, status));
            }

            // Load check-in records for the session
            List<CheckInRecord> checkIns = checkInRecordRepository.findBySessionId(sessionId);
            Map<String, CheckInRecord> checkInByUser = checkIns.stream()
                    .filter(c -> c.getStatus() == CheckInStatus.CHECKED_IN)
                    .collect(Collectors.toMap(
                            CheckInRecord::getUserId,
                            c -> c,
                            (existing, replacement) -> existing // keep first check-in
                    ));

            // Build CSV content
            String watermarkHeader = buildWatermarkHeader(operatorName, "Roster Export");
            String[] headers = {"User ID", "Username", "Display Name", "Registration Status", "Check-in Status", "Checked In At"};

            List<String[]> rows = new ArrayList<>();
            for (Registration reg : registrations) {
                User user = userRepository.findById(reg.getUserId()).orElse(null);
                String username = user != null ? user.getUsername() : "";
                String displayName = user != null ? user.getDisplayName() : "";

                CheckInRecord checkIn = checkInByUser.get(reg.getUserId());
                String checkInStatus = checkIn != null ? checkIn.getStatus().name() : "NOT_CHECKED_IN";
                String checkedInAt = checkIn != null && checkIn.getCheckedInAt() != null
                        ? ISO_FORMATTER.format(checkIn.getCheckedInAt()) : "";

                rows.add(new String[]{
                        reg.getUserId(),
                        username,
                        displayName,
                        reg.getStatus().name(),
                        checkInStatus,
                        checkedInAt
                });
            }

            // Write CSV to file
            String csvContent = renderCsv(watermarkHeader, headers, rows);
            String fileName = "roster_export_" + TIMESTAMP_FORMATTER.format(Instant.now()) + ".csv";
            Path filePath = writeExportFile(fileName, csvContent);

            // Update job with completion details
            job.setStatus(ExportStatus.COMPLETED);
            job.setFilePath(filePath.toString());
            job.setFileSizeBytes(Files.size(filePath));
            job.setRecordCount(registrations.size());
            job.setWatermarkText(watermarkHeader);
            job.setCompletedAt(Instant.now());
            job = exportJobRepository.save(job);

            auditService.log(AuditActionType.EXPORT_GENERATED,
                    requestedBy, operatorName, "SYSTEM",
                    "ExportJob", job.getId(),
                    "Roster export generated: " + registrations.size() + " records, session=" + sessionId);

            log.info("Roster export completed: jobId={}, records={}, sessionId={}",
                    job.getId(), registrations.size(), sessionId);

            return job;

        } catch (IOException e) {
            job.setStatus(ExportStatus.FAILED);
            job.setErrorMessage("Failed to write export file: " + e.getMessage());
            exportJobRepository.save(job);
            log.error("Roster export failed: jobId={}", job.getId(), e);
            throw new BusinessException("Export file generation failed", 500, "EXPORT_FAILED");
        }
    }

    // ------------------------------------------------------------------
    // Finance Report Export
    // ------------------------------------------------------------------

    /**
     * Generates a watermarked CSV export of finance postings for a given
     * accounting period. Each posting row includes a summary of its
     * allocation line items.
     *
     * @param periodId     the accounting period ID
     * @param requestedBy  the ID of the user requesting the export
     * @param operatorName the display name of the requesting operator
     * @return the completed {@link ExportJob} with file metadata
     * @throws BusinessException if download is denied by watermark policy
     */
    public ExportJob generateFinanceReport(String periodId, String requestedBy, String operatorName) {
        // Check watermark policy
        if (!isDownloadAllowed("FINANCE_REPORT", resolveRoleType(requestedBy))) {
            ExportJob denied = createExportJob("FINANCE_REPORT", requestedBy, null, ExportStatus.DENIED);
            exportJobRepository.save(denied);

            auditService.log(AuditActionType.EXPORT_DENIED,
                    requestedBy, operatorName, "SYSTEM",
                    "ExportJob", denied.getId(),
                    "Finance report export denied for operator: " + operatorName);

            throw new BusinessException("Export download is not permitted for your role", 403, "EXPORT_DENIED");
        }

        // Create export job
        ExportJob job = createExportJob("FINANCE_REPORT", requestedBy, "{\"periodId\":\"" + periodId + "\"}", ExportStatus.PENDING);
        job = exportJobRepository.save(job);

        job.setStatus(ExportStatus.GENERATING);
        job = exportJobRepository.save(job);

        try {
            // Load postings for the period
            List<PostingJournal> postings = postingJournalRepository.findByPeriodId(periodId);

            // Build a lookup for allocation rules
            Map<String, AllocationRule> ruleCache = allocationRuleRepository.findAll().stream()
                    .collect(Collectors.toMap(AllocationRule::getId, r -> r, (a, b) -> a));

            // Build CSV content
            String watermarkHeader = buildWatermarkHeader(operatorName, "Finance Report");
            String[] headers = {"Posting ID", "Period", "Rule", "Amount", "Status", "Posted By", "Posted At", "Line Items"};

            List<String[]> rows = new ArrayList<>();
            for (PostingJournal posting : postings) {
                // Resolve rule name
                AllocationRule rule = ruleCache.get(posting.getRuleId());
                String ruleName = rule != null ? rule.getName() : posting.getRuleId();

                // Summarize line items
                List<AllocationLineItem> lineItems = allocationLineItemRepository.findByPostingId(posting.getId());
                String lineItemSummary = lineItems.stream()
                        .map(li -> li.getAccountId() + ":" + li.getAmount().toPlainString())
                        .collect(Collectors.joining("; "));

                rows.add(new String[]{
                        posting.getId(),
                        periodId,
                        ruleName,
                        posting.getTotalAmount() != null ? posting.getTotalAmount().toPlainString() : "0",
                        posting.getStatus() != null ? posting.getStatus().name() : "",
                        posting.getPostedBy() != null ? posting.getPostedBy() : "",
                        posting.getPostedAt() != null ? ISO_FORMATTER.format(posting.getPostedAt()) : "",
                        lineItemSummary
                });
            }

            // Write CSV to file
            String csvContent = renderCsv(watermarkHeader, headers, rows);
            String fileName = "finance_report_" + TIMESTAMP_FORMATTER.format(Instant.now()) + ".csv";
            Path filePath = writeExportFile(fileName, csvContent);

            // Update job with completion details
            job.setStatus(ExportStatus.COMPLETED);
            job.setFilePath(filePath.toString());
            job.setFileSizeBytes(Files.size(filePath));
            job.setRecordCount(postings.size());
            job.setWatermarkText(watermarkHeader);
            job.setCompletedAt(Instant.now());
            job = exportJobRepository.save(job);

            auditService.log(AuditActionType.EXPORT_GENERATED,
                    requestedBy, operatorName, "SYSTEM",
                    "ExportJob", job.getId(),
                    "Finance report generated: " + postings.size() + " postings, period=" + periodId);

            log.info("Finance report completed: jobId={}, records={}, periodId={}",
                    job.getId(), postings.size(), periodId);

            return job;

        } catch (IOException e) {
            job.setStatus(ExportStatus.FAILED);
            job.setErrorMessage("Failed to write export file: " + e.getMessage());
            exportJobRepository.save(job);
            log.error("Finance report export failed: jobId={}", job.getId(), e);
            throw new BusinessException("Export file generation failed", 500, "EXPORT_FAILED");
        }
    }

    // ------------------------------------------------------------------
    // Download
    // ------------------------------------------------------------------

    /**
     * Downloads a previously generated export file.
     *
     * <p>The watermark policy is re-checked at download time so that a
     * policy change between generation and download is enforced.</p>
     *
     * @param exportId    the export job ID
     * @param requestedBy the ID of the user requesting the download
     * @return the raw file bytes
     * @throws BusinessException if the export is not found, not completed,
     *                           or download is denied by policy
     */
    public byte[] downloadExport(String exportId, String requestedBy) {
        ExportJob job = exportJobRepository.findById(exportId)
                .orElseThrow(() -> new BusinessException("Export job not found", 404, "NOT_FOUND"));

        String requesterRole = resolveRoleType(requestedBy);
        boolean systemAdmin = ROLE_SYSTEM_ADMIN.equals(requesterRole);

        if (!systemAdmin && !requestedBy.equals(job.getRequestedBy())) {
            throw new BusinessException("Export access denied for this user", 403, "EXPORT_SCOPE_DENIED");
        }

        if (!isRoleAllowedForExport(job.getExportType(), requesterRole)) {
            throw new BusinessException("Export type is not permitted for your role", 403, "EXPORT_ROLE_DENIED");
        }

        if (job.getStatus() != ExportStatus.COMPLETED) {
            throw new BusinessException("Export is not available for download (status=" + job.getStatus() + ")",
                    422, "EXPORT_NOT_READY");
        }

        // Re-check watermark policy at download time
        if (!isDownloadAllowed(job.getExportType(), requesterRole)) {
            throw new BusinessException("Export download is not permitted for your role", 403, "EXPORT_DENIED");
        }

        try {
            Path filePath = Paths.get(job.getFilePath());
            byte[] fileBytes = Files.readAllBytes(filePath);

            // Resolve operator name for audit logging
            String operatorName = resolveOperatorName(requestedBy);

            auditService.log(AuditActionType.EXPORT_GENERATED,
                    requestedBy, operatorName, "SYSTEM",
                    "ExportJob", job.getId(),
                    "Export downloaded: type=" + job.getExportType() + ", file=" + job.getFilePath());

            log.info("Export downloaded: jobId={}, requestedBy={}", exportId, requestedBy);

            return fileBytes;

        } catch (IOException e) {
            log.error("Failed to read export file: jobId={}, path={}", exportId, job.getFilePath(), e);
            throw new BusinessException("Export file could not be read", 500, "EXPORT_READ_FAILED");
        }
    }

    // ------------------------------------------------------------------
    // Policy Management
    // ------------------------------------------------------------------

    /**
     * Returns all active watermark policies.
     *
     * @return list of active {@link WatermarkPolicy} entries
     */
    @Transactional(readOnly = true)
    public List<WatermarkPolicy> getExportPolicies() {
        return watermarkPolicyRepository.findByActiveTrue();
    }

    /**
     * Updates a watermark policy's download permission and template.
     *
     * @param policyId           the policy ID
     * @param downloadAllowed    whether downloads are permitted
     * @param watermarkTemplate  the watermark template string
     * @return the updated policy
     * @throws BusinessException if the policy is not found
     */
    public WatermarkPolicy updatePolicy(String policyId, boolean downloadAllowed, String watermarkTemplate) {
        WatermarkPolicy policy = watermarkPolicyRepository.findById(policyId)
                .orElseThrow(() -> new BusinessException("Watermark policy not found", 404, "NOT_FOUND"));

        policy.setDownloadAllowed(downloadAllowed);
        policy.setWatermarkTemplate(watermarkTemplate);

        WatermarkPolicy saved = watermarkPolicyRepository.save(policy);

        log.info("Watermark policy updated: id={}, reportType={}, roleType={}, downloadAllowed={}",
                saved.getId(), saved.getReportType(), saved.getRoleType(), downloadAllowed);

        return saved;
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Builds a two-line watermark comment header for inclusion at the top
     * of an exported CSV file.
     *
     * @param operatorName the name of the operator generating the export
     * @param reportType   the human-readable report type label
     * @return the watermark header string
     */
    private String buildWatermarkHeader(String operatorName, String reportType) {
        String timestamp = ISO_FORMATTER.format(Instant.now());
        return "# Generated by: " + operatorName + " | At: " + timestamp + " | Report: " + reportType + "\n"
                + "# This document is confidential and watermarked.";
    }

    /**
     * Checks whether downloads are allowed for the given report type and
     * role. If no matching policy exists, downloads are allowed by default.
     *
     * @param reportType the export report type (e.g. AUDIT_LOG, ROSTER)
     * @param roleType   the user's role type
     * @return {@code true} if download is permitted
     */
    private boolean isDownloadAllowed(String reportType, String roleType) {
        List<WatermarkPolicy> policies = watermarkPolicyRepository.findByReportTypeAndRoleType(reportType, roleType);
        if (policies.isEmpty()) {
            // No policy found — default to allowed
            return true;
        }
        return policies.get(0).isDownloadAllowed();
    }

    private boolean isRoleAllowedForExport(String exportType, String roleType) {
        Set<String> allowedRoles = new HashSet<>();
        if ("AUDIT_LOG".equals(exportType)) {
            allowedRoles.add(ROLE_SYSTEM_ADMIN);
        } else if ("ROSTER".equals(exportType)) {
            allowedRoles.add(ROLE_EVENT_STAFF);
            allowedRoles.add(ROLE_SYSTEM_ADMIN);
        } else if ("FINANCE_REPORT".equals(exportType)) {
            allowedRoles.add(ROLE_FINANCE_MANAGER);
            allowedRoles.add(ROLE_SYSTEM_ADMIN);
        }

        return allowedRoles.contains(roleType);
    }

    /**
     * Resolves the role type string for a given user ID. Returns
     * {@code "UNKNOWN"} if the user cannot be found.
     */
    private String resolveRoleType(String userId) {
        return userRepository.findById(userId)
                .map(user -> user.getRoleType().name())
                .orElse("UNKNOWN");
    }

    /**
     * Resolves the display name for a given user ID. Returns the user ID
     * itself if the user cannot be found.
     */
    private String resolveOperatorName(String userId) {
        return userRepository.findById(userId)
                .map(User::getDisplayName)
                .orElse(userId);
    }

    /**
     * Creates a new {@link ExportJob} entity with the given initial state.
     */
    private ExportJob createExportJob(String exportType, String requestedBy,
                                      String filterCriteria, ExportStatus status) {
        ExportJob job = new ExportJob();
        job.setExportType(exportType);
        job.setRequestedBy(requestedBy);
        job.setFilterCriteria(filterCriteria);
        job.setStatus(status);
        return job;
    }

    /**
     * Parses the JSON filter criteria and queries audit events accordingly.
     * Supports optional filter keys: {@code dateFrom} (ISO instant),
     * {@code dateTo} (ISO instant), and {@code actionType} (enum name).
     *
     * <p>If no filter criteria are provided or the JSON is empty, all
     * audit events are returned.</p>
     */
    private List<AuditEvent> queryAuditEvents(String filterCriteria) {
        if (filterCriteria == null || filterCriteria.isBlank()) {
            return auditEventRepository.findAll();
        }

        try {
            JsonNode root = objectMapper.readTree(filterCriteria);

            String dateFromStr = root.has("dateFrom") ? root.get("dateFrom").asText(null) : null;
            String dateToStr = root.has("dateTo") ? root.get("dateTo").asText(null) : null;
            String actionTypeStr = root.has("actionType") ? root.get("actionType").asText(null) : null;

            // If both date range and action type are specified, filter by date first then action type in-memory
            if (dateFromStr != null && dateToStr != null) {
                Instant dateFrom = Instant.parse(dateFromStr);
                Instant dateTo = Instant.parse(dateToStr);
                List<AuditEvent> events = auditEventRepository.findByCreatedAtBetween(dateFrom, dateTo);

                if (actionTypeStr != null && !actionTypeStr.isBlank()) {
                    AuditActionType actionType = AuditActionType.valueOf(actionTypeStr);
                    return events.stream()
                            .filter(e -> e.getActionType() == actionType)
                            .collect(Collectors.toList());
                }
                return events;
            }

            // Action type only
            if (actionTypeStr != null && !actionTypeStr.isBlank()) {
                AuditActionType actionType = AuditActionType.valueOf(actionTypeStr);
                return auditEventRepository.findByActionType(actionType);
            }

            // No recognized filters — return all
            return auditEventRepository.findAll();

        } catch (IOException e) {
            log.warn("Failed to parse filter criteria JSON, returning all audit events: {}", e.getMessage());
            return auditEventRepository.findAll();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid filter value in criteria, returning all audit events: {}", e.getMessage());
            return auditEventRepository.findAll();
        }
    }

    /**
     * Renders CSV content to a string using {@link CsvGenerator}.
     */
    private String renderCsv(String watermarkHeader, String[] headers, List<String[]> rows) throws IOException {
        StringWriter writer = new StringWriter();
        CsvGenerator.writeCsv(writer, watermarkHeader, headers, rows);
        return writer.toString();
    }

    /**
     * Writes CSV content to a file under the configured storage path.
     * Creates the storage directory if it does not already exist.
     *
     * @param fileName   the file name (not a full path)
     * @param csvContent the CSV content to write
     * @return the resolved file path
     * @throws IOException if the directory cannot be created or the file cannot be written
     */
    private Path writeExportFile(String fileName, String csvContent) throws IOException {
        Path directory = Paths.get(storagePath);
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }
        Path filePath = directory.resolve(fileName);
        Files.writeString(filePath, csvContent, StandardCharsets.UTF_8);
        return filePath;
    }
}
