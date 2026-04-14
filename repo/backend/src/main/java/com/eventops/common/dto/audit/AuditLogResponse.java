package com.eventops.common.dto.audit;

import java.time.Instant;
import java.util.List;

public class AuditLogResponse {
    private String id;
    private String actionType;
    private String operatorId;
    private String operatorName;
    private String requestSource;
    private String entityType;
    private String entityId;
    private String description;
    private List<FieldDiffResponse> fieldDiffs;
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
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
    public List<FieldDiffResponse> getFieldDiffs() { return fieldDiffs; }
    public void setFieldDiffs(List<FieldDiffResponse> fieldDiffs) { this.fieldDiffs = fieldDiffs; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public static class FieldDiffResponse {
        private String fieldName;
        private String oldValue;
        private String newValue;

        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }
        public String getOldValue() { return oldValue; }
        public void setOldValue(String oldValue) { this.oldValue = oldValue; }
        public String getNewValue() { return newValue; }
        public void setNewValue(String newValue) { this.newValue = newValue; }
    }
}
