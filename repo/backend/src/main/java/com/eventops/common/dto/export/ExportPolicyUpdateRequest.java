package com.eventops.common.dto.export;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ExportPolicyUpdateRequest {

    @NotBlank
    private String id;

    @NotNull
    private Boolean downloadAllowed;

    @Size(max = 500)
    private String watermarkTemplate;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Boolean getDownloadAllowed() {
        return downloadAllowed;
    }

    public void setDownloadAllowed(Boolean downloadAllowed) {
        this.downloadAllowed = downloadAllowed;
    }

    public String getWatermarkTemplate() {
        return watermarkTemplate;
    }

    public void setWatermarkTemplate(String watermarkTemplate) {
        this.watermarkTemplate = watermarkTemplate;
    }
}
