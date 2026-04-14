package com.eventops.audit;

import com.eventops.audit.diff.FieldDiffCalculator;
import com.eventops.audit.logging.FieldChange;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AuditFieldDiffTest {

    @Test
    void computeDiffs_detectsChangedField() {
        Map<String, String> before = Map.of("name", "Alice");
        Map<String, String> after = Map.of("name", "Bob");
        List<FieldChange> diffs = FieldDiffCalculator.computeDiffs(before, after);
        assertEquals(1, diffs.size());
        assertEquals("name", diffs.get(0).fieldName());
        assertEquals("Alice", diffs.get(0).oldValue());
        assertEquals("Bob", diffs.get(0).newValue());
    }

    @Test
    void computeDiffs_detectsAddedField() {
        Map<String, String> before = Map.of();
        Map<String, String> after = Map.of("email", "a@b.com");
        List<FieldChange> diffs = FieldDiffCalculator.computeDiffs(before, after);
        assertEquals(1, diffs.size());
        assertEquals("email", diffs.get(0).fieldName());
    }

    @Test
    void computeDiffs_detectsRemovedField() {
        Map<String, String> before = Map.of("phone", "555-1234");
        Map<String, String> after = Map.of();
        List<FieldChange> diffs = FieldDiffCalculator.computeDiffs(before, after);
        assertEquals(1, diffs.size());
    }

    @Test
    void computeDiffs_noDiffsForIdentical() {
        Map<String, String> before = Map.of("name", "Alice");
        Map<String, String> after = Map.of("name", "Alice");
        List<FieldChange> diffs = FieldDiffCalculator.computeDiffs(before, after);
        assertTrue(diffs.isEmpty());
    }

    @Test
    void computeDiffs_redactsSensitiveFields() {
        Map<String, String> before = Map.of("passwordHash", "oldhash");
        Map<String, String> after = Map.of("passwordHash", "newhash");
        List<FieldChange> diffs = FieldDiffCalculator.computeDiffs(before, after);
        assertEquals(1, diffs.size());
        assertEquals("[REDACTED]", diffs.get(0).oldValue());
        assertEquals("[REDACTED]", diffs.get(0).newValue());
    }

    @Test
    void computeDiffs_redactsContactInfo() {
        Map<String, String> before = Map.of("contactInfo", "old@email.com");
        Map<String, String> after = Map.of("contactInfo", "new@email.com");
        List<FieldChange> diffs = FieldDiffCalculator.computeDiffs(before, after);
        assertEquals("[REDACTED]", diffs.get(0).oldValue());
        assertEquals("[REDACTED]", diffs.get(0).newValue());
    }

    @Test
    void computeDiffs_handlesNullMaps() {
        List<FieldChange> diffs = FieldDiffCalculator.computeDiffs(null, null);
        assertTrue(diffs.isEmpty());
    }
}
