package com.eventops.domain.finance;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "allocation_line_items")
public class AllocationLineItem {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "posting_id", nullable = false, length = 36)
    private String postingId;

    @Column(name = "account_id", nullable = false, length = 36)
    private String accountId;

    @Column(name = "cost_center_id", length = 36)
    private String costCenterId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(length = 500)
    private String description;

    @Column(name = "recognition_start")
    private java.time.LocalDate recognitionStart;

    @Column(name = "recognition_end")
    private java.time.LocalDate recognitionEnd;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPostingId() { return postingId; }
    public void setPostingId(String postingId) { this.postingId = postingId; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getCostCenterId() { return costCenterId; }
    public void setCostCenterId(String costCenterId) { this.costCenterId = costCenterId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public java.time.LocalDate getRecognitionStart() { return recognitionStart; }
    public void setRecognitionStart(java.time.LocalDate recognitionStart) { this.recognitionStart = recognitionStart; }
    public java.time.LocalDate getRecognitionEnd() { return recognitionEnd; }
    public void setRecognitionEnd(java.time.LocalDate recognitionEnd) { this.recognitionEnd = recognitionEnd; }
    public Instant getCreatedAt() { return createdAt; }
}
