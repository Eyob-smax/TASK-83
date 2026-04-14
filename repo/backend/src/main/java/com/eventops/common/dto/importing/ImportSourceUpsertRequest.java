package com.eventops.common.dto.importing;

import com.eventops.domain.importing.ImportMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ImportSourceUpsertRequest {

    @NotBlank
    @Size(max = 200)
    private String name;

    @NotBlank
    @Size(max = 500)
    private String folderPath;

    @NotBlank
    @Size(max = 200)
    private String filePattern;

    @NotNull
    private ImportMode importMode;

    private String columnMappings;

    @Min(1)
    private int concurrencyCap;

    @Min(1)
    private int timeoutSeconds;

    @Min(1)
    private int circuitBreakerThreshold;

    @NotNull
    private Boolean active;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFolderPath() {
        return folderPath;
    }

    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath;
    }

    public String getFilePattern() {
        return filePattern;
    }

    public void setFilePattern(String filePattern) {
        this.filePattern = filePattern;
    }

    public ImportMode getImportMode() {
        return importMode;
    }

    public void setImportMode(ImportMode importMode) {
        this.importMode = importMode;
    }

    public String getColumnMappings() {
        return columnMappings;
    }

    public void setColumnMappings(String columnMappings) {
        this.columnMappings = columnMappings;
    }

    public int getConcurrencyCap() {
        return concurrencyCap;
    }

    public void setConcurrencyCap(int concurrencyCap) {
        this.concurrencyCap = concurrencyCap;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getCircuitBreakerThreshold() {
        return circuitBreakerThreshold;
    }

    public void setCircuitBreakerThreshold(int circuitBreakerThreshold) {
        this.circuitBreakerThreshold = circuitBreakerThreshold;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
