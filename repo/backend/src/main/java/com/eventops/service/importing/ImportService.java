package com.eventops.service.importing;

import com.eventops.audit.logging.AuditService;
import com.eventops.common.dto.importing.ImportSourceUpsertRequest;
import com.eventops.common.dto.importing.ImportSourceResponse;
import com.eventops.common.exception.BusinessException;
import com.eventops.domain.audit.AuditActionType;
import com.eventops.domain.importing.CrawlJob;
import com.eventops.domain.importing.ImportJobStatus;
import com.eventops.domain.importing.ImportMode;
import com.eventops.domain.importing.ImportSource;
import com.eventops.repository.importing.CrawlJobRepository;
import com.eventops.repository.importing.ImportSourceRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manages shared-folder import sources, crawl job lifecycle, file processing
 * with incremental checkpoint support, and circuit-breaker protection.
 */
@Service
@Transactional
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);
    private static final int DEFAULT_PRIORITY = 100;
    private static final int MIN_PRIORITY = 1;
    private static final int MAX_PRIORITY = 1000;

    private final ImportSourceRepository importSourceRepository;
    private final CrawlJobRepository crawlJobRepository;
    private final AuditService auditService;
    private final SharedFolderPathResolver sharedFolderPathResolver;

    public ImportService(ImportSourceRepository importSourceRepository,
                         CrawlJobRepository crawlJobRepository,
                         AuditService auditService,
                         SharedFolderPathResolver sharedFolderPathResolver) {
        this.importSourceRepository = importSourceRepository;
        this.crawlJobRepository = crawlJobRepository;
        this.auditService = auditService;
        this.sharedFolderPathResolver = sharedFolderPathResolver;
    }

    /**
     * Creates and queues a new crawl job for the given import source.
     *
     * @param sourceId the import source identifier
     * @param mode     the import mode (INCREMENTAL or FULL)
     * @return the created crawl job
     * @throws BusinessException if the source is not found (404) or inactive (422)
     */
    public CrawlJob triggerCrawl(String sourceId, ImportMode mode) {
        return triggerCrawl(sourceId, mode, null);
    }

    public CrawlJob triggerCrawl(String sourceId, ImportMode mode, Integer priority) {
        ImportSource source = importSourceRepository.findById(sourceId)
                .orElseThrow(() -> new BusinessException("Import source not found", 404, "NOT_FOUND"));

        if (!source.isActive()) {
            throw new BusinessException("Import source is inactive", 422, "SOURCE_INACTIVE");
        }

        CrawlJob job = new CrawlJob();
        job.setSourceId(sourceId);
        job.setImportMode(mode);
        job.setStatus(ImportJobStatus.QUEUED);
        job.setPriority(resolvePriority(priority));

        CrawlJob saved = crawlJobRepository.save(job);

        auditService.log(
                AuditActionType.IMPORT_STARTED,
                "SYSTEM",
                "ImportService",
                "SYSTEM",
                "CrawlJob",
                saved.getId(),
                "Import crawl queued: sourceId=" + sourceId + ", mode=" + mode + ", priority=" + saved.getPriority()
        );

            log.info("Crawl job created: id={}, sourceId={}, mode={}, priority={}",
                saved.getId(), sourceId, mode, saved.getPriority());

        startIfUnderCap(saved);

        return saved;
    }

    /**
     * Transitions a queued job to RUNNING if the source's concurrency cap
     * has not been reached.
     *
     * @param job the queued crawl job
     */
    public void startIfUnderCap(CrawlJob job) {
        long running = crawlJobRepository.countBySourceIdAndStatus(job.getSourceId(), ImportJobStatus.RUNNING);
        ImportSource source = importSourceRepository.findById(job.getSourceId())
                .orElseThrow(() -> new BusinessException("Import source not found", 404, "NOT_FOUND"));

        if (running < source.getConcurrencyCap()) {
            job.setStatus(ImportJobStatus.RUNNING);
            job.setStartedAt(Instant.now());
            crawlJobRepository.save(job);
            log.info("Crawl job started: id={}, sourceId={}, runningCount={}", job.getId(), job.getSourceId(), running + 1);
        } else {
            log.debug("Crawl job remains queued (cap reached): id={}, sourceId={}, running={}, cap={}",
                    job.getId(), job.getSourceId(), running, source.getConcurrencyCap());
        }
    }

    /**
     * Executes the file crawl for a given job. Reads files from the source's folder
     * path matching the configured file pattern. Supports incremental mode via
     * checkpoint markers and circuit-breaker protection on consecutive failures.
     *
     * <p>This method catches all I/O exceptions gracefully so the scheduler is
     * never crashed by missing or inaccessible paths.</p>
     *
     * @param job the crawl job to execute
     */
    public void executeCrawl(CrawlJob job) {
        ImportSource source = importSourceRepository.findById(job.getSourceId())
                .orElseThrow(() -> new BusinessException("Import source not found", 404, "NOT_FOUND"));

        Path folderPath;
        try {
            folderPath = sharedFolderPathResolver.resolveForExecution(source.getFolderPath());
        } catch (Exception e) {
            log.error("Invalid folder path for source {}: {}", source.getId(), source.getFolderPath());
            job.setStatus(ImportJobStatus.FAILED);
            job.setErrorMessage("Invalid folder path: " + source.getFolderPath());
            job.setCompletedAt(Instant.now());
            crawlJobRepository.save(job);
            auditService.log(
                    AuditActionType.IMPORT_FAILED,
                    "SYSTEM",
                    "ImportService",
                    "SYSTEM",
                    "CrawlJob",
                    job.getId(),
                    "Invalid folder path"
            );
            return;
        }

        if (!Files.exists(folderPath) || !Files.isDirectory(folderPath)) {
            log.error("Folder path does not exist or is not a directory: {}", folderPath);
            job.setStatus(ImportJobStatus.FAILED);
            job.setErrorMessage("Folder path does not exist: " + source.getFolderPath());
            job.setCompletedAt(Instant.now());
            crawlJobRepository.save(job);
            auditService.log(
                    AuditActionType.IMPORT_FAILED,
                    "SYSTEM",
                    "ImportService",
                    "SYSTEM",
                    "CrawlJob",
                    job.getId(),
                    "Folder path does not exist"
            );
            return;
        }

        List<Path> matchingFiles;
        try {
            matchingFiles = collectMatchingFiles(folderPath, source.getFilePattern());
        } catch (IOException e) {
            log.error("Failed to list files in folder {}: {}", folderPath, e.getMessage());
            job.setStatus(ImportJobStatus.FAILED);
            job.setErrorMessage("Failed to list files: " + e.getMessage());
            job.setCompletedAt(Instant.now());
            crawlJobRepository.save(job);
            auditService.log(
                    AuditActionType.IMPORT_FAILED,
                    "SYSTEM",
                    "ImportService",
                    "SYSTEM",
                    "CrawlJob",
                    job.getId(),
                    "Failed to list files in folder"
            );
            return;
        }

        // Sort alphabetically for deterministic ordering
        matchingFiles.sort((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));

        // Incremental mode: filter files after checkpoint marker
        if (job.getImportMode() == ImportMode.INCREMENTAL && job.getCheckpointMarker() != null) {
            String checkpoint = job.getCheckpointMarker();
            matchingFiles = matchingFiles.stream()
                    .filter(p -> p.getFileName().toString().compareTo(checkpoint) > 0)
                    .collect(Collectors.toList());
        }

        log.info("Processing {} files for job {}", matchingFiles.size(), job.getId());

        Instant deadline = job.getStartedAt() != null
                ? job.getStartedAt().plusSeconds(source.getTimeoutSeconds())
                : Instant.now().plusSeconds(source.getTimeoutSeconds());

        for (Path file : matchingFiles) {
            // Enforce per-job timeout before each file is processed
            if (Instant.now().isAfter(deadline)) {
                job.setStatus(ImportJobStatus.FAILED);
                job.setErrorMessage("Job timeout exceeded (" + source.getTimeoutSeconds() + "s) at file: " + file.getFileName());
                job.setCompletedAt(Instant.now());
                crawlJobRepository.save(job);
                auditService.log(
                        AuditActionType.IMPORT_FAILED,
                        "SYSTEM", "ImportService", "SYSTEM",
                        "CrawlJob", job.getId(),
                        "Import timeout exceeded after " + source.getTimeoutSeconds() + "s"
                );
                log.warn("Crawl job timed out: id={}, timeout={}s", job.getId(), source.getTimeoutSeconds());
                return;
            }

            try {
                List<String> lines = Files.readAllLines(file);
                int recordCount = lines.size();

                job.setFilesProcessed(job.getFilesProcessed() + 1);
                job.setRecordsImported(job.getRecordsImported() + recordCount);
                job.setCheckpointMarker(file.getFileName().toString());
                job.setConsecutiveFailures(0);

                log.debug("Processed file: name={}, records={}, jobId={}",
                        file.getFileName(), recordCount, job.getId());

            } catch (Exception e) {
                log.error("Failed to process file: name={}, jobId={}", file.getFileName(), job.getId(), e);

                job.setRecordsFailed(job.getRecordsFailed() + 1);
                job.setConsecutiveFailures(job.getConsecutiveFailures() + 1);

                // Circuit breaker check
                if (job.getConsecutiveFailures() >= source.getCircuitBreakerThreshold()) {
                    job.setStatus(ImportJobStatus.CIRCUIT_BROKEN);
                    job.setErrorMessage("Circuit breaker tripped after " + job.getConsecutiveFailures()
                            + " consecutive failures at file: " + file.getFileName());
                    crawlJobRepository.save(job);

                    auditService.log(
                            AuditActionType.CIRCUIT_BREAKER_TRIPPED,
                            "SYSTEM",
                            "ImportService",
                            "SYSTEM",
                            "CrawlJob",
                            job.getId(),
                            "Circuit breaker tripped at file: " + file.getFileName()
                    );

                    log.warn("Circuit breaker tripped: jobId={}, consecutiveFailures={}",
                            job.getId(), job.getConsecutiveFailures());
                    return;
                }
            }
        }

        // Determine final status
        if (job.getRecordsFailed() > 0 && job.getRecordsImported() == 0) {
            job.setStatus(ImportJobStatus.FAILED);
        } else {
            job.setStatus(ImportJobStatus.COMPLETED);
        }
        job.setCompletedAt(Instant.now());
        crawlJobRepository.save(job);

        AuditActionType auditAction = (job.getStatus() == ImportJobStatus.FAILED)
                ? AuditActionType.IMPORT_FAILED
                : AuditActionType.IMPORT_COMPLETED;

        auditService.log(
                auditAction,
                "SYSTEM",
                "ImportService",
                "SYSTEM",
                "CrawlJob",
                job.getId(),
                "Import " + job.getStatus().name().toLowerCase()
                        + ": files=" + job.getFilesProcessed()
                        + ", imported=" + job.getRecordsImported()
                        + ", failed=" + job.getRecordsFailed()
        );

        log.info("Crawl job finished: id={}, status={}, files={}, imported={}, failed={}",
                job.getId(), job.getStatus(), job.getFilesProcessed(),
                job.getRecordsImported(), job.getRecordsFailed());
    }

    /**
     * Returns all import sources mapped to response DTOs.
     *
     * @return list of import source responses
     */
    @Transactional(readOnly = true)
    public List<ImportSourceResponse> getSources() {
        return importSourceRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public ImportSourceResponse createSource(ImportSourceUpsertRequest request) {
        ImportSource source = new ImportSource();
        applySourceRequest(source, request);
        ImportSource saved = importSourceRepository.save(source);
        return mapToResponse(saved);
    }

    public ImportSourceResponse updateSource(String sourceId, ImportSourceUpsertRequest request) {
        ImportSource source = importSourceRepository.findById(sourceId)
                .orElseThrow(() -> new BusinessException("Import source not found", 404, "NOT_FOUND"));
        applySourceRequest(source, request);
        ImportSource saved = importSourceRepository.save(source);
        return mapToResponse(saved);
    }

    /**
     * Returns crawl jobs, optionally filtered by source ID, with pagination.
     *
     * @param sourceId optional source ID filter (null for all jobs)
     * @param pageable pagination parameters
     * @return page of crawl jobs
     */
    @Transactional(readOnly = true)
    public Page<CrawlJob> getJobs(String sourceId, Pageable pageable) {
        if (sourceId != null && !sourceId.isBlank()) {
            return crawlJobRepository.findAll(
                    (root, query, cb) -> cb.equal(root.get("sourceId"), sourceId),
                    pageable
            );
        }
        return crawlJobRepository.findAll(pageable);
    }

    /**
     * Returns a single crawl job by ID.
     *
     * @param jobId the crawl job identifier
     * @return the crawl job
     * @throws BusinessException if not found
     */
    @Transactional(readOnly = true)
    public CrawlJob getJob(String jobId) {
        return crawlJobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException("Crawl job not found", 404, "NOT_FOUND"));
    }

    /**
     * Returns circuit breaker status information for all active import sources.
     * For each source, reports the count of currently running and circuit-broken jobs.
     *
     * @return map of source IDs to their circuit breaker status
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCircuitBreakerStatus() {
        List<ImportSource> activeSources = importSourceRepository.findByActiveTrue();
        Map<String, Object> status = new HashMap<>();

        for (ImportSource source : activeSources) {
            long runningCount = crawlJobRepository.countBySourceIdAndStatus(
                    source.getId(), ImportJobStatus.RUNNING);
            long circuitBrokenCount = crawlJobRepository.countBySourceIdAndStatus(
                    source.getId(), ImportJobStatus.CIRCUIT_BROKEN);

            Map<String, Object> sourceStatus = new HashMap<>();
            sourceStatus.put("sourceName", source.getName());
            sourceStatus.put("runningJobs", runningCount);
            sourceStatus.put("circuitBrokenJobs", circuitBrokenCount);
            sourceStatus.put("concurrencyCap", source.getConcurrencyCap());
            sourceStatus.put("circuitBreakerThreshold", source.getCircuitBreakerThreshold());

            status.put(source.getId(), sourceStatus);
        }

        return status;
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Collects files in the given directory that match the glob file pattern.
     */
    private List<Path> collectMatchingFiles(Path folder, String filePattern) throws IOException {
        List<Path> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, filePattern)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    result.add(entry);
                }
            }
        }
        return result;
    }

    /**
     * Maps an {@link ImportSource} entity to an {@link ImportSourceResponse} DTO.
     */
    private ImportSourceResponse mapToResponse(ImportSource source) {
        ImportSourceResponse dto = new ImportSourceResponse();
        dto.setId(source.getId());
        dto.setName(source.getName());
        dto.setFolderPath(source.getFolderPath());
        dto.setFilePattern(source.getFilePattern());
        dto.setImportMode(source.getImportMode().name());
        dto.setConcurrencyCap(source.getConcurrencyCap());
        dto.setTimeoutSeconds(source.getTimeoutSeconds());
        dto.setCircuitBreakerThreshold(source.getCircuitBreakerThreshold());
        dto.setActive(source.isActive());
        dto.setCreatedAt(source.getCreatedAt());
        return dto;
    }

    private void applySourceRequest(ImportSource source, ImportSourceUpsertRequest request) {
        source.setName(request.getName());
        source.setFolderPath(sharedFolderPathResolver.sanitizeForStorage(request.getFolderPath()));
        source.setFilePattern(request.getFilePattern());
        source.setImportMode(request.getImportMode());
        source.setColumnMappings(request.getColumnMappings());
        source.setConcurrencyCap(request.getConcurrencyCap());
        source.setTimeoutSeconds(request.getTimeoutSeconds());
        source.setCircuitBreakerThreshold(request.getCircuitBreakerThreshold());
        source.setActive(Boolean.TRUE.equals(request.getActive()));
    }

    private int resolvePriority(Integer priority) {
        if (priority == null) {
            return DEFAULT_PRIORITY;
        }

        if (priority < MIN_PRIORITY || priority > MAX_PRIORITY) {
            throw new BusinessException(
                    "priority must be between " + MIN_PRIORITY + " and " + MAX_PRIORITY,
                    422,
                    "INVALID_PRIORITY"
            );
        }

        return priority;
    }
}
