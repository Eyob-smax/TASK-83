package com.eventops.controller.importing;

import com.eventops.common.dto.ApiResponse;
import com.eventops.common.dto.PagedResponse;
import com.eventops.common.dto.importing.ImportSourceUpsertRequest;
import com.eventops.common.dto.importing.ImportSourceResponse;
import com.eventops.common.exception.BusinessException;
import com.eventops.domain.importing.CrawlJob;
import com.eventops.domain.importing.ImportMode;
import com.eventops.service.importing.ImportService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for managing shared-folder imports: listing sources,
 * browsing crawl jobs, triggering new crawls, and monitoring circuit breaker status.
 *
 * <p>All endpoints return the standard {@link ApiResponse} envelope.</p>
 */
@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private static final Logger log = LoggerFactory.getLogger(ImportController.class);

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    /**
     * Returns all configured import sources.
     *
     * @return 200 with list of import sources
     */
    @GetMapping("/sources")
    public ResponseEntity<ApiResponse<List<ImportSourceResponse>>> getSources() {
        log.debug("GET /api/imports/sources");

        List<ImportSourceResponse> sources = importService.getSources();
        return ResponseEntity.ok(ApiResponse.success(sources));
    }

    @PostMapping("/sources")
    public ResponseEntity<ApiResponse<ImportSourceResponse>> createSource(
            @Valid @RequestBody ImportSourceUpsertRequest request) {
        ImportSourceResponse response = importService.createSource(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Import source created"));
    }

    @PutMapping("/sources/{id}")
    public ResponseEntity<ApiResponse<ImportSourceResponse>> updateSource(
            @PathVariable String id,
            @Valid @RequestBody ImportSourceUpsertRequest request) {
        ImportSourceResponse response = importService.updateSource(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Import source updated"));
    }

    /**
     * Returns crawl jobs with optional filtering by source ID and pagination.
     *
     * @param sourceId optional source ID filter
     * @param pageable pagination parameters
     * @return 200 with paginated crawl job list
     */
    @GetMapping("/jobs")
    public ResponseEntity<ApiResponse<PagedResponse<CrawlJob>>> getJobs(
            @RequestParam(required = false) String sourceId,
            Pageable pageable) {
        log.debug("GET /api/imports/jobs – sourceId={}, page={}", sourceId, pageable);

        Page<CrawlJob> page = importService.getJobs(sourceId, pageable);
        PagedResponse<CrawlJob> pagedResponse = new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );

        return ResponseEntity.ok(ApiResponse.success(pagedResponse));
    }

    /**
     * Retrieves a single crawl job by ID.
     *
     * @param id the crawl job identifier
     * @return 200 with crawl job data
     */
    @GetMapping("/jobs/{id}")
    public ResponseEntity<ApiResponse<CrawlJob>> getJob(@PathVariable String id) {
        log.debug("GET /api/imports/jobs/{}", id);

        CrawlJob job = importService.getJob(id);
        return ResponseEntity.ok(ApiResponse.success(job));
    }

    /**
     * Triggers a new crawl job for the specified import source.
     *
     * @param body request body containing "sourceId", optional "mode",
     *             and optional "priority"
     *             (defaults to INCREMENTAL)
     * @return 201 with the created crawl job
     */
    @PostMapping("/jobs/trigger")
    public ResponseEntity<ApiResponse<CrawlJob>> trigger(@RequestBody Map<String, Object> body) {
        String sourceId = body.get("sourceId") != null ? body.get("sourceId").toString() : null;
        String modeStr = body.get("mode") != null ? body.get("mode").toString() : "INCREMENTAL";
        Integer priority = parsePriority(body.get("priority"));
        ImportMode mode = ImportMode.valueOf(modeStr.toUpperCase());
        log.debug("POST /api/imports/jobs/trigger – sourceId={}, mode={}, priority={}", sourceId, mode, priority);

        CrawlJob job = importService.triggerCrawl(sourceId, mode, priority);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(job, "Import job triggered"));
    }

    private Integer parsePriority(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException ex) {
            throw new BusinessException("priority must be a number", 422, "INVALID_PRIORITY");
        }
    }

    /**
     * Returns circuit breaker status for all active import sources.
     *
     * @return 200 with circuit breaker status map
     */
    @GetMapping("/circuit-breaker")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCircuitBreakerStatus() {
        log.debug("GET /api/imports/circuit-breaker");

        Map<String, Object> status = importService.getCircuitBreakerStatus();
        return ResponseEntity.ok(ApiResponse.success(status));
    }
}
