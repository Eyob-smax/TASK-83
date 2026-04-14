package com.eventops.service.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.eventops.common.exception.BusinessException;
import com.eventops.domain.audit.ExportJob;
import com.eventops.domain.audit.WatermarkPolicy;
import com.eventops.repository.audit.ExportJobRepository;
import com.eventops.repository.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Manages export job lifecycle: creating export requests for audit logs,
 * rosters, and finance reports; downloading generated files; and managing
 * watermark/download policies.
 */
@Service("lifecycleExportService")
@Transactional
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private final ExportJobRepository exportJobRepository;
    private final UserRepository userRepository;
    private final com.eventops.service.audit.ExportService auditExportService;
    private final ObjectMapper objectMapper;

    public ExportService(ExportJobRepository exportJobRepository,
                         UserRepository userRepository,
                         com.eventops.service.audit.ExportService auditExportService,
                         ObjectMapper objectMapper) {
        this.exportJobRepository = exportJobRepository;
        this.userRepository = userRepository;
        this.auditExportService = auditExportService;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates an export job for audit logs with the given filter criteria.
     *
     * @param filterCriteria JSON filter criteria
     * @param requestedBy    the user requesting the export
     * @return the created export job
     */
    public ExportJob exportAuditLogs(Map<String, Object> filterCriteria, String requestedBy) {
        String operatorName = resolveOperatorName(requestedBy);
        String serializedFilter = serializeFilterCriteria(filterCriteria);
        log.info("Delegating audit export generation for user {}", requestedBy);
        return auditExportService.generateAuditExport(requestedBy, operatorName, serializedFilter);
    }

    /**
     * Creates an export job for a session roster.
     *
     * @param sessionId   the session to export
     * @param requestedBy the user requesting the export
     * @return the created export job
     */
    public ExportJob generateRosterExport(String sessionId, String requestedBy) {
        String operatorName = resolveOperatorName(requestedBy);
        log.info("Delegating roster export generation for session {} and user {}", sessionId, requestedBy);
        return auditExportService.generateRosterExport(sessionId, requestedBy, operatorName);
    }

    /**
     * Creates an export job for a finance report.
     *
     * @param periodId    the accounting period to export
     * @param requestedBy the user requesting the export
     * @return the created export job
     */
    public ExportJob generateFinanceReport(String periodId, String requestedBy) {
        String operatorName = resolveOperatorName(requestedBy);
        log.info("Delegating finance export generation for period {} and user {}", periodId, requestedBy);
        return auditExportService.generateFinanceReport(periodId, requestedBy, operatorName);
    }

    /**
     * Downloads the file associated with an export job.
     *
     * @param exportId    the export job ID
     * @param requestedBy the user requesting the download
     * @return the file bytes
     * @throws BusinessException if the export is not found, not completed, or the file is missing
     */
    public byte[] downloadExport(String exportId, String requestedBy) {
        return auditExportService.downloadExport(exportId, requestedBy);
    }

    /**
     * Returns the file name for an export job.
     *
     * @param exportId the export job ID
     * @return the file name
     */
    @Transactional(readOnly = true)
    public String getExportFileName(String exportId) {
        ExportJob job = exportJobRepository.findById(exportId)
                .orElseThrow(() -> new BusinessException("Export job not found", 404, "NOT_FOUND"));
        if (job.getFilePath() != null) {
            return Paths.get(job.getFilePath()).getFileName().toString();
        }
        return "export_" + exportId + ".dat";
    }

    /**
     * Returns all watermark/download policies.
     *
     * @return list of watermark policies
     */
    @Transactional(readOnly = true)
    public List<WatermarkPolicy> getExportPolicies() {
        return auditExportService.getExportPolicies();
    }

    /**
     * Updates a watermark/download policy.
     *
     * @param policyId          the policy ID
     * @param downloadAllowed   whether downloads are allowed
     * @param watermarkTemplate the watermark template text
     * @return the updated policy
     */
    public WatermarkPolicy updatePolicy(String policyId, boolean downloadAllowed, String watermarkTemplate) {
        return auditExportService.updatePolicy(policyId, downloadAllowed, watermarkTemplate);
    }

    private String resolveOperatorName(String userId) {
        return userRepository.findById(userId)
                .map(user -> user.getDisplayName() != null ? user.getDisplayName() : user.getUsername())
                .orElse(userId);
    }

    private String serializeFilterCriteria(Map<String, Object> filterCriteria) {
        if (filterCriteria == null || filterCriteria.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(filterCriteria);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize export filter criteria, falling back to toString(): {}", e.getMessage());
            return filterCriteria.toString();
        }
    }
}
