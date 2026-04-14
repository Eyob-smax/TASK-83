/**
 * Audit infrastructure: immutable log writing, field-level diffs, CSV export.
 *
 * <p>Ensures audit records are INSERT-only. Provides diff computation
 * independent of domain entities and CSV export with watermark injection.</p>
 */
package com.eventops.audit;
