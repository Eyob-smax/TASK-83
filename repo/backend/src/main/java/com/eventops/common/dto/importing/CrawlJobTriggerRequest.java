package com.eventops.common.dto.importing;

import com.eventops.domain.importing.ImportMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class CrawlJobTriggerRequest {

    @NotBlank
    private String sourceId;

    private ImportMode mode = ImportMode.INCREMENTAL;

    @Min(1)
    private Integer priority;

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public ImportMode getMode() { return mode; }
    public void setMode(ImportMode mode) { this.mode = mode; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
}
