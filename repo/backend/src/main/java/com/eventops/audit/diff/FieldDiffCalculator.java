package com.eventops.audit.diff;

import com.eventops.audit.logging.FieldChange;

import java.util.*;

/**
 * Pure-logic utility that computes field-level diffs between two snapshots of
 * an entity's fields.
 *
 * <p>This class is <b>not</b> a Spring bean — it is designed for direct static
 * invocation and straightforward unit testing with no Spring context required.</p>
 *
 * <p>Values for fields whose names appear in {@link #SENSITIVE_FIELDS} are
 * replaced with {@code [REDACTED]} in the resulting {@link FieldChange} records
 * so that sensitive data never leaks into the audit trail.</p>
 */
public final class FieldDiffCalculator {

    /** The replacement string applied to sensitive field values. */
    private static final String REDACTED = "[REDACTED]";

    /**
     * Field names whose values must be redacted in diff output.
     *
     * <p>The set is mutable so that callers (e.g., configuration code) can add
     * application-specific sensitive fields at startup. Thread-safe mutation is
     * the caller's responsibility; during normal operation the set is
     * effectively read-only.</p>
     */
    public static final Set<String> SENSITIVE_FIELDS = new LinkedHashSet<>(
            Set.of(
                    "passwordHash",
                    "contactInfo",
                    "deviceFingerprint"
            )
    );

    private FieldDiffCalculator() {
        // utility class — not instantiable
    }

    /**
     * Compares two maps of {@code fieldName -> stringValue} and returns a
     * {@link FieldChange} for every field that differs between the two
     * snapshots.
     *
     * <ul>
     *   <li>A field present in {@code before} but not in {@code after} is
     *       treated as a removal (newValue is {@code null}).</li>
     *   <li>A field present in {@code after} but not in {@code before} is
     *       treated as an addition (oldValue is {@code null}).</li>
     *   <li>{@code null} values are normalised to the empty string for
     *       comparison purposes. A change from {@code null} to {@code ""}
     *       (or vice-versa) is <b>not</b> reported as a diff.</li>
     *   <li>Fields listed in {@link #SENSITIVE_FIELDS} have their old and new
     *       values replaced with {@code [REDACTED]}.</li>
     * </ul>
     *
     * @param before field snapshot before the change (may be {@code null})
     * @param after  field snapshot after the change (may be {@code null})
     * @return an unmodifiable list of {@link FieldChange} entries; never
     *         {@code null}, but may be empty
     */
    public static List<FieldChange> computeDiffs(Map<String, String> before,
                                                  Map<String, String> after) {
        Map<String, String> safeB = before != null ? before : Collections.emptyMap();
        Map<String, String> safeA = after != null ? after : Collections.emptyMap();

        Set<String> allFields = new TreeSet<>();
        allFields.addAll(safeB.keySet());
        allFields.addAll(safeA.keySet());

        List<FieldChange> changes = new ArrayList<>();

        for (String field : allFields) {
            String oldVal = normalise(safeB.get(field));
            String newVal = normalise(safeA.get(field));

            if (oldVal.equals(newVal)) {
                continue;
            }

            // Restore null semantics for absent keys.
            String oldOut = safeB.containsKey(field) ? safeB.get(field) : null;
            String newOut = safeA.containsKey(field) ? safeA.get(field) : null;

            if (SENSITIVE_FIELDS.contains(field)) {
                oldOut = oldOut != null ? REDACTED : null;
                newOut = newOut != null ? REDACTED : null;
            }

            changes.add(new FieldChange(field, oldOut, newOut));
        }

        return Collections.unmodifiableList(changes);
    }

    /**
     * Normalises a value for comparison: {@code null} becomes the empty
     * string.
     */
    private static String normalise(String value) {
        return value == null ? "" : value;
    }
}
