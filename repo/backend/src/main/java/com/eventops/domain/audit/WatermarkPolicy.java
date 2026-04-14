package com.eventops.domain.audit;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "watermark_policies")
public class WatermarkPolicy {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "report_type", nullable = false, length = 50)
    private String reportType;

    @Column(name = "role_type", nullable = false, length = 30)
    private String roleType;

    @Column(name = "download_allowed", nullable = false)
    private boolean downloadAllowed = true;

    @Column(name = "watermark_template", length = 500)
    private String watermarkTemplate;

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
    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }
    public String getRoleType() { return roleType; }
    public void setRoleType(String roleType) { this.roleType = roleType; }
    public boolean isDownloadAllowed() { return downloadAllowed; }
    public void setDownloadAllowed(boolean downloadAllowed) { this.downloadAllowed = downloadAllowed; }
    public String getWatermarkTemplate() { return watermarkTemplate; }
    public void setWatermarkTemplate(String watermarkTemplate) { this.watermarkTemplate = watermarkTemplate; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
