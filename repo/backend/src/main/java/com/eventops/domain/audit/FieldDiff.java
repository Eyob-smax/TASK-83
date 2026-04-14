package com.eventops.domain.audit;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Field-level diff entry linked to an audit event. Immutable.
 */
@Entity
@Table(name = "field_diffs")
public class FieldDiff {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "audit_event_id", nullable = false, length = 36)
    private String auditEventId;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAuditEventId() { return auditEventId; }
    public void setAuditEventId(String auditEventId) { this.auditEventId = auditEventId; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }
    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }
    public Instant getCreatedAt() { return createdAt; }
}
