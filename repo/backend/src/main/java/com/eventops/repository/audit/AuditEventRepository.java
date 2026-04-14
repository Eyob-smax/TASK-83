package com.eventops.repository.audit;

import com.eventops.domain.audit.AuditEvent;
import com.eventops.domain.audit.AuditActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.time.Instant;
import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, String>, JpaSpecificationExecutor<AuditEvent> {
    List<AuditEvent> findByActionType(AuditActionType actionType);
    List<AuditEvent> findByOperatorId(String operatorId);
    List<AuditEvent> findByEntityTypeAndEntityId(String entityType, String entityId);
    List<AuditEvent> findByCreatedAtBetween(Instant from, Instant to);
}
