package com.eventops.domain.finance;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Allocation/recognition rule with version tracking.
 * Each update creates a new version — existing postings reference the version they used.
 */
@Entity
@Table(name = "allocation_rules")
public class AllocationRule {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_method", nullable = false, length = 20)
    private AllocationMethod allocationMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "recognition_method", nullable = false, length = 30)
    private RevenueRecognitionMethod recognitionMethod;

    @Column(name = "account_id", length = 36)
    private String accountId;

    @Column(name = "cost_center_id", length = 36)
    private String costCenterId;

    @Column(name = "rule_config", columnDefinition = "TEXT")
    private String ruleConfig; // JSON — tiered thresholds, fixed amounts, proportional weights

    @Column(nullable = false)
    private int version = 1;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by", length = 36)
    private String createdBy;

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
    public AllocationMethod getAllocationMethod() { return allocationMethod; }
    public void setAllocationMethod(AllocationMethod allocationMethod) { this.allocationMethod = allocationMethod; }
    public RevenueRecognitionMethod getRecognitionMethod() { return recognitionMethod; }
    public void setRecognitionMethod(RevenueRecognitionMethod recognitionMethod) { this.recognitionMethod = recognitionMethod; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getCostCenterId() { return costCenterId; }
    public void setCostCenterId(String costCenterId) { this.costCenterId = costCenterId; }
    public String getRuleConfig() { return ruleConfig; }
    public void setRuleConfig(String ruleConfig) { this.ruleConfig = ruleConfig; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
