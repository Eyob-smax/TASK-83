package com.eventops.domain.importing;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "crawl_jobs", indexes = {
    @Index(name = "idx_crawl_job_trace", columnList = "trace_id")
})
public class CrawlJob {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "source_id", nullable = false, length = 36)
    private String sourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "import_mode", nullable = false, length = 20)
    private ImportMode importMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImportJobStatus status = ImportJobStatus.QUEUED;

    @Column(nullable = false)
    private int priority = 100;

    @Column(name = "trace_id", nullable = false, length = 36)
    private String traceId;

    @Column(name = "files_processed", nullable = false)
    private int filesProcessed = 0;

    @Column(name = "records_imported", nullable = false)
    private int recordsImported = 0;

    @Column(name = "records_failed", nullable = false)
    private int recordsFailed = 0;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures = 0;

    @Column(name = "checkpoint_marker", length = 500)
    private String checkpointMarker;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (traceId == null) traceId = java.util.UUID.randomUUID().toString();
        createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public ImportMode getImportMode() { return importMode; }
    public void setImportMode(ImportMode importMode) { this.importMode = importMode; }
    public ImportJobStatus getStatus() { return status; }
    public void setStatus(ImportJobStatus status) { this.status = status; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public int getFilesProcessed() { return filesProcessed; }
    public void setFilesProcessed(int filesProcessed) { this.filesProcessed = filesProcessed; }
    public int getRecordsImported() { return recordsImported; }
    public void setRecordsImported(int recordsImported) { this.recordsImported = recordsImported; }
    public int getRecordsFailed() { return recordsFailed; }
    public void setRecordsFailed(int recordsFailed) { this.recordsFailed = recordsFailed; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(int consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; }
    public String getCheckpointMarker() { return checkpointMarker; }
    public void setCheckpointMarker(String checkpointMarker) { this.checkpointMarker = checkpointMarker; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
