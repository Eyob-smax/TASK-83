package com.eventops.service.audit;

import com.eventops.common.exception.BusinessException;
import com.eventops.domain.audit.AuditActionType;
import com.eventops.domain.audit.AuditEvent;
import com.eventops.domain.audit.FieldDiff;
import com.eventops.repository.audit.AuditEventRepository;
import com.eventops.repository.audit.FieldDiffRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Read-only service for querying audit logs and their field-level diffs.
 *
 * <p>This is intentionally separate from {@link com.eventops.audit.logging.AuditService}
 * which handles write operations. Keeping reads and writes in separate services
 * enforces a clean separation of concerns and allows independent scaling of
 * the query path.</p>
 */
@Service
@Transactional(readOnly = true)
public class AuditQueryService {

    private static final Logger log = LoggerFactory.getLogger(AuditQueryService.class);

    private final AuditEventRepository auditEventRepository;
    private final FieldDiffRepository fieldDiffRepository;

    public AuditQueryService(AuditEventRepository auditEventRepository,
                             FieldDiffRepository fieldDiffRepository) {
        this.auditEventRepository = auditEventRepository;
        this.fieldDiffRepository = fieldDiffRepository;
    }

    /**
     * Searches audit logs with dynamic filtering. All filter parameters are optional;
     * only non-null values are applied as predicates.
     *
     * @param actionType the audit action type to filter by (exact match)
     * @param operatorId the operator user ID to filter by (exact match)
     * @param entityType the entity type to filter by (exact match)
     * @param entityId   the entity ID to filter by (exact match)
     * @param dateFrom   the lower bound for createdAt (inclusive)
     * @param dateTo     the upper bound for createdAt (inclusive)
     * @param pageable   pagination and sorting parameters
     * @return a page of matching audit events
     */
    public Page<AuditEvent> searchLogs(String actionType, String operatorId,
                                       String entityType, String entityId,
                                       Instant dateFrom, Instant dateTo,
                                       Pageable pageable) {
        log.debug("Searching audit logs: actionType={}, operatorId={}, entityType={}, entityId={}, dateFrom={}, dateTo={}",
                actionType, operatorId, entityType, entityId, dateFrom, dateTo);

        Specification<AuditEvent> spec = Specification.where(null);

        if (actionType != null && !actionType.isBlank()) {
            AuditActionType parsedAction = AuditActionType.valueOf(actionType);
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("actionType"), parsedAction));
        }

        if (operatorId != null && !operatorId.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("operatorId"), operatorId));
        }

        if (entityType != null && !entityType.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("entityType"), entityType));
        }

        if (entityId != null && !entityId.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("entityId"), entityId));
        }

        if (dateFrom != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("createdAt"), dateFrom));
        }

        if (dateTo != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("createdAt"), dateTo));
        }

        Page<AuditEvent> results = auditEventRepository.findAll(spec, pageable);
        log.debug("Audit log search returned {} results (page {} of {})",
                results.getNumberOfElements(), results.getNumber(), results.getTotalPages());

        return results;
    }

    /**
     * Retrieves a single audit event by ID.
     *
     * @param id the audit event identifier
     * @return the audit event
     * @throws BusinessException if the audit event does not exist
     */
    public AuditEvent getLog(String id) {
        return auditEventRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Audit event not found", 404, "NOT_FOUND"));
    }

    /**
     * Returns all field-level diffs associated with an audit event.
     *
     * @param auditEventId the audit event identifier
     * @return list of field diffs for the event
     */
    public List<FieldDiff> getFieldDiffs(String auditEventId) {
        return fieldDiffRepository.findByAuditEventId(auditEventId);
    }
}
