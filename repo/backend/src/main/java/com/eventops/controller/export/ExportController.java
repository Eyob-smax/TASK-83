package com.eventops.controller.export;

import com.eventops.common.dto.ApiResponse;
import com.eventops.common.dto.export.ExportPolicyUpdateRequest;
import com.eventops.common.dto.export.FinanceReportRequest;
import com.eventops.common.dto.export.RosterExportRequest;
import com.eventops.domain.audit.ExportJob;
import com.eventops.domain.audit.WatermarkPolicy;
import com.eventops.security.auth.EventOpsUserDetails;
import com.eventops.service.export.ExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing data exports: generating roster and finance
 * report exports, downloading export artifacts, and managing export policies.
 *
 * <p>All endpoints return the standard {@link ApiResponse} envelope unless
 * returning raw file bytes for downloads.</p>
 */
@RestController
@RequestMapping("/api/exports")
public class ExportController {

    private static final Logger log = LoggerFactory.getLogger(ExportController.class);

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    /**
     * Generates a roster export for the specified session.
     *
     * @param body      request body containing "sessionId"
     * @param principal the authenticated user
     * @return 201 with the created export job
     */
    @PostMapping("/rosters")
    public ResponseEntity<ApiResponse<ExportJob>> generateRosterExport(
            @Valid @RequestBody RosterExportRequest request,
            @AuthenticationPrincipal EventOpsUserDetails principal) {
        String userId = principal.getUser().getId();
        log.debug("POST /api/exports/rosters – sessionId={}, userId={}", request.getSessionId(), userId);

        ExportJob job = exportService.generateRosterExport(request.getSessionId(), userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(job, "Roster export initiated"));
    }

    /**
     * Generates a finance report export for the specified accounting period.
     *
     * @param body      request body containing "periodId"
     * @param principal the authenticated user
     * @return 201 with the created export job
     */
    @PostMapping("/finance-reports")
    public ResponseEntity<ApiResponse<ExportJob>> generateFinanceReport(
            @Valid @RequestBody FinanceReportRequest request,
            @AuthenticationPrincipal EventOpsUserDetails principal) {
        String userId = principal.getUser().getId();
        log.debug("POST /api/exports/finance-reports – periodId={}, userId={}", request.getPeriodId(), userId);

        ExportJob job = exportService.generateFinanceReport(request.getPeriodId(), userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(job, "Finance report export initiated"));
    }

    /**
     * Downloads the file associated with an export job.
     *
     * @param id        the export job identifier
     * @param principal the authenticated user
     * @return the export file bytes with Content-Disposition attachment header
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadExport(
            @PathVariable String id,
            @AuthenticationPrincipal EventOpsUserDetails principal) {
        String userId = principal.getUser().getId();
        log.debug("GET /api/exports/{}/download – userId={}", id, userId);

        byte[] data = exportService.downloadExport(id, userId);
        String fileName = exportService.getExportFileName(id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(data.length)
                .body(data);
    }

    /**
     * Returns all export/download policies (watermark and access rules).
     *
     * @return 200 with list of export policies
     */
    @GetMapping("/policies")
    public ResponseEntity<ApiResponse<List<WatermarkPolicy>>> getExportPolicies() {
        log.debug("GET /api/exports/policies");

        List<WatermarkPolicy> policies = exportService.getExportPolicies();
        return ResponseEntity.ok(ApiResponse.success(policies));
    }

    /**
     * Updates an export/download policy.
     *
     * @param id   the policy identifier
     * @param body request body containing "downloadAllowed" and "watermarkTemplate"
     * @return 200 with the updated policy
     */
    @PutMapping("/policies")
    public ResponseEntity<ApiResponse<WatermarkPolicy>> updatePolicy(
            @RequestBody ExportPolicyUpdateRequest request) {
        boolean downloadAllowed = Boolean.TRUE.equals(request.getDownloadAllowed());
        String watermarkTemplate = request.getWatermarkTemplate();
        log.debug("PUT /api/exports/policies/{} – downloadAllowed={}", request.getId(), downloadAllowed);

        WatermarkPolicy policy = exportService.updatePolicy(request.getId(), downloadAllowed, watermarkTemplate);
        return ResponseEntity.ok(ApiResponse.success(policy, "Export policy updated"));
    }
}
