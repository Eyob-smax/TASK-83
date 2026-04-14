/**
 * Audit domain: AuditEvent, FieldDiff, ExportJob entities.
 *
 * <p>Models immutable audit log entries capturing operator identity,
 * timestamp, request source, action type, and field-level diffs for
 * key entity changes. Audit records are INSERT-only — no UPDATE or
 * DELETE operations are permitted.</p>
 */
package com.eventops.domain.audit;
