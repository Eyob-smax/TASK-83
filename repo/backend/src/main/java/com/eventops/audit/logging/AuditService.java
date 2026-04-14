package com.eventops.audit.logging;

import com.eventops.domain.audit.AuditActionType;
import com.eventops.domain.audit.AuditEvent;
import com.eventops.domain.audit.FieldDiff;
import com.eventops.repository.audit.AuditEventRepository;
import com.eventops.repository.audit.FieldDiffRepository;
import com.eventops.security.auth.EventOpsUserDetails;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Locale;

/**
 * Central service for writing immutable audit log entries.
 *
 * <p>This service enforces the core invariant of the audit log:
 * <b>records are INSERT-only</b>. There are no update or delete methods.
 * All writes are performed inside a transaction so that the
 * {@link AuditEvent} and its associated {@link FieldDiff} records are
 * persisted atomically.</p>
 *
 * <p>SLF4J logging is emitted at INFO level for every audit write, but
 * <b>field values are never logged</b> — only action types, entity
 * identifiers, and field names are included in the log output.</p>
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final String REDACTED = "[REDACTED]";

    private final AuditEventRepository auditEventRepository;
    private final FieldDiffRepository fieldDiffRepository;

    public AuditService(AuditEventRepository auditEventRepository,
                        FieldDiffRepository fieldDiffRepository) {
        this.auditEventRepository = auditEventRepository;
        this.fieldDiffRepository = fieldDiffRepository;
    }

    /**
     * Creates and persists an immutable audit event.
     *
     * @param actionType    the type of action being audited
     * @param operatorId    the ID of the user who performed the action
     * @param operatorName  the display name of the operator
     * @param requestSource a label identifying the request origin (e.g., "WEB", "API", "SYSTEM")
     * @param entityType    the type of entity affected (e.g., "User", "Event")
     * @param entityId      the ID of the affected entity
     * @param description   a human-readable description of the action
     * @return the persisted {@link AuditEvent} with its generated ID and timestamp
     */
    @Transactional
    public AuditEvent log(AuditActionType actionType,
                          String operatorId,
                          String operatorName,
                          String requestSource,
                          String entityType,
                          String entityId,
                          String description) {

        AuditEvent event = buildAuditEvent(actionType, operatorId, operatorName,
                requestSource, entityType, entityId, description);
        AuditEvent saved = auditEventRepository.save(event);

        logAuditWrite(saved, 0);
        return saved;
    }

    /**
     * Creates and persists an immutable audit event together with its
     * field-level diff records.
     *
     * <p>The event and all diffs are written within a single transaction so
     * that they are committed atomically.</p>
     *
     * @param actionType    the type of action being audited
     * @param operatorId    the ID of the user who performed the action
     * @param operatorName  the display name of the operator
     * @param requestSource a label identifying the request origin
     * @param entityType    the type of entity affected
     * @param entityId      the ID of the affected entity
     * @param description   a human-readable description of the action
     * @param fieldChanges  the list of field-level changes to record
     * @return the persisted {@link AuditEvent}
     */
    @Transactional
    public AuditEvent logWithDiffs(AuditActionType actionType,
                                   String operatorId,
                                   String operatorName,
                                   String requestSource,
                                   String entityType,
                                   String entityId,
                                   String description,
                                   List<FieldChange> fieldChanges) {

        AuditEvent event = buildAuditEvent(actionType, operatorId, operatorName,
                requestSource, entityType, entityId, description);
        AuditEvent saved = auditEventRepository.save(event);

        int diffCount = 0;
        if (fieldChanges != null) {
            for (FieldChange change : fieldChanges) {
                FieldDiff diff = new FieldDiff();
                diff.setAuditEventId(saved.getId());
                diff.setFieldName(change.fieldName());
                diff.setOldValue(redactFieldValue(change.fieldName(), change.oldValue()));
                diff.setNewValue(redactFieldValue(change.fieldName(), change.newValue()));
                fieldDiffRepository.save(diff);
                diffCount++;
            }
        }

        logAuditWrite(saved, diffCount);
        return saved;
    }

    /**
     * Convenience method that extracts the current authenticated user from
     * {@link SecurityContextHolder} and the request source from
     * {@link RequestContextHolder}, then delegates to
     * {@link #log(AuditActionType, String, String, String, String, String, String)}.
     *
     * @param actionType  the type of action being audited
     * @param entityType  the type of entity affected
     * @param entityId    the ID of the affected entity
     * @param description a human-readable description
     * @return the persisted {@link AuditEvent}
     * @throws IllegalStateException if no authenticated user is found in the
     *                               security context
     */
    @Transactional
    public AuditEvent logForCurrentUser(AuditActionType actionType,
                                        String entityType,
                                        String entityId,
                                        String description) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof EventOpsUserDetails userDetails)) {
            throw new IllegalStateException(
                    "Cannot write audit log for current user: no authenticated EventOpsUserDetails found in SecurityContext");
        }

        String operatorId = userDetails.getUser().getId();
        String operatorName = userDetails.getUsername();
        String requestSource = resolveRequestSource();

        return log(actionType, operatorId, operatorName, requestSource, entityType, entityId, description);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Builds a new {@link AuditEvent} entity. The entity's ID and
     * {@code createdAt} are populated by the JPA {@code @PrePersist}
     * callback.
     */
    private AuditEvent buildAuditEvent(AuditActionType actionType,
                                       String operatorId,
                                       String operatorName,
                                       String requestSource,
                                       String entityType,
                                       String entityId,
                                       String description) {
        AuditEvent event = new AuditEvent();
        event.setActionType(actionType);
        event.setOperatorId(operatorId);
        event.setOperatorName(operatorName);
        event.setRequestSource(requestSource);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setDescription(LogSanitizer.sanitize(description));
        return event;
    }

    /**
     * Emits an INFO-level log line for the audit write. Only the action
     * type, entity information, and field-change <em>count</em> are
     * logged — <b>never</b> the field values.
     */
    private void logAuditWrite(AuditEvent event, int diffCount) {
        if (diffCount > 0) {
            log.info("Audit event recorded: id={}, action={}, entityType={}, entityId={}, operatorId={}, diffs={}",
                    event.getId(),
                    event.getActionType(),
                    event.getEntityType(),
                    event.getEntityId(),
                    event.getOperatorId(),
                    diffCount);
        } else {
            log.info("Audit event recorded: id={}, action={}, entityType={}, entityId={}, operatorId={}",
                    event.getId(),
                    event.getActionType(),
                    event.getEntityType(),
                    event.getEntityId(),
                    event.getOperatorId());
        }
    }

    /**
     * Resolves a human-readable request source label from the current
     * HTTP request (via {@link RequestContextHolder}), or returns
     * {@code "SYSTEM"} if no request context is available.
     */
    private String resolveRequestSource() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String remoteAddr = request.getRemoteAddr();
                String userAgent = request.getHeader("User-Agent");
                if (userAgent != null && userAgent.toLowerCase().contains("mobile")) {
                    return "MOBILE:" + remoteAddr;
                }
                return "WEB:" + remoteAddr;
            }
        } catch (Exception e) {
            log.debug("Could not resolve request source from RequestContextHolder", e);
        }
        return "SYSTEM";
    }

    private String redactFieldValue(String fieldName, String value) {
        if (value == null) {
            return null;
        }

        String normalized = fieldName == null ? "" : fieldName.toLowerCase(Locale.ROOT);
        if (isSensitiveField(normalized)) {
            return REDACTED;
        }

        return LogSanitizer.sanitize(value);
    }

    private boolean isSensitiveField(String normalizedFieldName) {
        return normalizedFieldName.contains("contact")
                || normalizedFieldName.contains("email")
                || normalizedFieldName.contains("phone")
                || normalizedFieldName.contains("password")
                || normalizedFieldName.contains("secret")
                || normalizedFieldName.contains("token")
                || normalizedFieldName.contains("hash")
                || normalizedFieldName.contains("fingerprint")
                || normalizedFieldName.contains("device")
                || normalizedFieldName.contains("signature")
                || normalizedFieldName.contains("passcode")
                || normalizedFieldName.contains("key");
    }
}
