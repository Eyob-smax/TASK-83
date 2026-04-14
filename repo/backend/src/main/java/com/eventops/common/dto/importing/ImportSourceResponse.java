package com.eventops.common.dto.importing;

import java.time.Instant;

public class ImportSourceResponse {
    private String id;
    private String name;
    private String folderPath;
    private String filePattern;
    private String importMode;
    private int concurrencyCap;
    private int timeoutSeconds;
    private int circuitBreakerThreshold;
    private boolean active;
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFolderPath() { return folderPath; }
    public void setFolderPath(String folderPath) { this.folderPath = folderPath; }
    public String getFilePattern() { return filePattern; }
    public void setFilePattern(String filePattern) { this.filePattern = filePattern; }
    public String getImportMode() { return importMode; }
    public void setImportMode(String importMode) { this.importMode = importMode; }
    public int getConcurrencyCap() { return concurrencyCap; }
    public void setConcurrencyCap(int concurrencyCap) { this.concurrencyCap = concurrencyCap; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public int getCircuitBreakerThreshold() { return circuitBreakerThreshold; }
    public void setCircuitBreakerThreshold(int t) { this.circuitBreakerThreshold = t; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
