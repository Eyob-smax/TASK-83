package com.eventops.domain.importing;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "import_sources")
public class ImportSource {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "folder_path", nullable = false, length = 500)
    private String folderPath;

    @Column(name = "file_pattern", nullable = false, length = 200)
    private String filePattern;

    @Enumerated(EnumType.STRING)
    @Column(name = "import_mode", nullable = false, length = 20)
    private ImportMode importMode = ImportMode.INCREMENTAL;

    @Column(name = "column_mappings", columnDefinition = "TEXT")
    private String columnMappings; // JSON config

    @Column(name = "concurrency_cap", nullable = false)
    private int concurrencyCap = 3;

    @Column(name = "timeout_seconds", nullable = false)
    private int timeoutSeconds = 30;

    @Column(name = "circuit_breaker_threshold", nullable = false)
    private int circuitBreakerThreshold = 10;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = Instant.now(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFolderPath() { return folderPath; }
    public void setFolderPath(String folderPath) { this.folderPath = folderPath; }
    public String getFilePattern() { return filePattern; }
    public void setFilePattern(String filePattern) { this.filePattern = filePattern; }
    public ImportMode getImportMode() { return importMode; }
    public void setImportMode(ImportMode importMode) { this.importMode = importMode; }
    public String getColumnMappings() { return columnMappings; }
    public void setColumnMappings(String columnMappings) { this.columnMappings = columnMappings; }
    public int getConcurrencyCap() { return concurrencyCap; }
    public void setConcurrencyCap(int concurrencyCap) { this.concurrencyCap = concurrencyCap; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public int getCircuitBreakerThreshold() { return circuitBreakerThreshold; }
    public void setCircuitBreakerThreshold(int t) { this.circuitBreakerThreshold = t; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
