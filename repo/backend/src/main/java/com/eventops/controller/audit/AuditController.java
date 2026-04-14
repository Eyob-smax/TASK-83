package com.eventops.controller.audit;

import com.eventops.common.dto.ApiResponse;
import com.eventops.common.dto.PagedResponse;
import com.eventops.common.dto.audit.AuditLogResponse;
import com.eventops.common.dto.audit.AuditLogResponse.FieldDiffResponse;
import com.eventops.domain.audit.AuditEvent;
import com.eventops.domain.audit.ExportJob;
import com.eventops.domain.audit.FieldDiff;
import com.eventops.security.auth.EventOpsUserDetails;
import com.eventops.service.audit.AuditQueryService;
import com.eventops.service.export.ExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST controller for querying audit logs, viewing field-level diffs,
 * exporting audit data, and downloading export artifacts.
 *
 * <p>All endpoints return the standard {@link ApiResponse} envelope unless
 * returning raw file bytes for downloads.</p>
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private static final Logger log = LoggerFactory.getLogger(AuditController.class);

    private final AuditQueryService auditQueryService;
    private final ExportService exportService;

        public AuditController(AuditQueryService auditQueryService, ExportService exportService) {
        this.auditQueryService = auditQueryService;
        this.exportService = exportService;
    }

    /**
     * Searches audit logs with optional filtering by action type, operator,
     * entity type/ID, and date range.
     *
     * @param actionType optional action type filter
     * @param operatorId optional operator user ID filter
     * @param entityType optional entity type filter
     * @param entityId   optional entity ID filter
     * @param dateFrom   optional lower bound for createdAt (inclusive)
     * @param dateTo     optional upper bound for createdAt (inclusive)
     * @param pageable   pagination and sorting parameters
     * @return 200 with paginated audit log results
     */
    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<PagedResponse<AuditEvent>>> searchLogs(
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String operatorId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) Instant dateFrom,
            @RequestParam(required = false) Instant dateTo,
            Pageable pageable) {
        log.debug("GET /api/audit/logs – actionType={}, operatorId={}, entityType={}, entityId={}, dateFrom={}, dateTo={}",
                actionType, operatorId, entityType, entityId, dateFrom, dateTo);

        Page<AuditEvent> page = auditQueryService.searchLogs(
                actionType, operatorId, entityType, entityId, dateFrom, dateTo, pageable);
        PagedResponse<AuditEvent> pagedResponse = new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );

        return ResponseEntity.ok(ApiResponse.success(pagedResponse));
    }

    /**
     * Retrieves a single audit log entry with its field-level diffs.
     *
     * @param id the audit event identifier
     * @return 200 with audit log details including field diffs
     */
    @GetMapping("/logs/{id}")
    public ResponseEntity<ApiResponse<AuditLogResponse>> getLog(@PathVariable String id) {
        log.debug("GET /api/audit/logs/{}", id);

        AuditEvent event = auditQueryService.getLog(id);
        List<FieldDiff> diffs = auditQueryService.getFieldDiffs(id);

        AuditLogResponse response = new AuditLogResponse();
        response.setId(event.getId());
        response.setActionType(event.getActionType().name());
        response.setOperatorId(event.getOperatorId());
        response.setOperatorName(event.getOperatorName());
        response.setRequestSource(event.getRequestSource());
        response.setEntityType(event.getEntityType());
        response.setEntityId(event.getEntityId());
        response.setDescription(event.getDescription());
        response.setCreatedAt(event.getCreatedAt());

        List<FieldDiffResponse> fieldDiffResponses = diffs.stream()
                .map(diff -> {
                    FieldDiffResponse fdr = new FieldDiffResponse();
                    fdr.setFieldName(diff.getFieldName());
                    fdr.setOldValue(diff.getOldValue());
                    fdr.setNewValue(diff.getNewValue());
                    return fdr;
                })
                .toList();
        response.setFieldDiffs(fieldDiffResponses);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Triggers an export of audit logs based on the provided filter criteria.
     *
     * @param filterCriteria the filter criteria map
     * @param principal      the authenticated user
     * @return 201 with the created export job
     */
    @PostMapping("/logs/export")
    public ResponseEntity<ApiResponse<ExportJob>> exportLogs(
            @RequestBody Map<String, Object> filterCriteria,
            @AuthenticationPrincipal EventOpsUserDetails principal) {
        String userId = principal.getUser().getId();
        log.debug("POST /api/audit/logs/export – userId={}", userId);

        ExportJob job = exportService.exportAuditLogs(filterCriteria, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(job, "Audit log export initiated"));
    }

    /**
     * Downloads the file associated with an export job.
     *
     * @param id        the export job identifier
     * @param principal the authenticated user
     * @return the export file bytes with Content-Disposition attachment header
     */
    @GetMapping("/exports/{id}/download")
    public ResponseEntity<byte[]> downloadExport(
            @PathVariable String id,
            @AuthenticationPrincipal EventOpsUserDetails principal) {
        String userId = principal.getUser().getId();
        log.debug("GET /api/audit/exports/{}/download – userId={}", id, userId);

        byte[] data = exportService.downloadExport(id, userId);
        String fileName = exportService.getExportFileName(id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(data.length)
                .body(data);
    }
}
