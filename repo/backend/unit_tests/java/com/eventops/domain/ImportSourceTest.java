package com.eventops.domain;

import com.eventops.domain.importing.ImportSource;
import com.eventops.domain.importing.ImportMode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImportSourceTest {

    @Test
    void defaultConcurrencyCap_is3() {
        ImportSource source = new ImportSource();
        assertEquals(3, source.getConcurrencyCap());
    }

    @Test
    void defaultTimeoutSeconds_is30() {
        ImportSource source = new ImportSource();
        assertEquals(30, source.getTimeoutSeconds());
    }

    @Test
    void defaultCircuitBreakerThreshold_is10() {
        ImportSource source = new ImportSource();
        assertEquals(10, source.getCircuitBreakerThreshold());
    }

    @Test
    void defaultImportMode_isIncremental() {
        ImportSource source = new ImportSource();
        assertEquals(ImportMode.INCREMENTAL, source.getImportMode());
    }
}
