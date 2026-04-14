package com.eventops.domain.audit;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Immutable audit log entry. INSERT-only — no UPDATE or DELETE permitted.
 */
@Entity
@Table(name = "audit_events", indexes = {
    @Index(name = "idx_audit_timestamp", columnList = "created_at"),
    @Index(name = "idx_audit_action", columnList = "action_type"),
    @Index(name = "idx_audit_operator", columnList = "operator_id"),
    @Index(name = "idx_audit_entity", columnList = "entity_type,entity_id")
})
public class AuditEvent {

    @Id
    @Column(length = 36)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 40)
    private AuditActionType actionType;

    @Column(name = "operator_id", nullable = false, length = 36)
    private String operatorId;

    @Column(name = "operator_name", nullable = false, length = 200)
    private String operatorName;

    @Column(name = "request_source", length = 50)
    private String requestSource;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id", length = 36)
    private String entityId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public AuditActionType getActionType() { return actionType; }
    public void setActionType(AuditActionType actionType) { this.actionType = actionType; }
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getRequestSource() { return requestSource; }
    public void setRequestSource(String requestSource) { this.requestSource = requestSource; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getCreatedAt() { return createdAt; }
}
