package com.eventops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Scaffold test verifying the Spring Boot application class exists
 * and is properly annotated.
 *
 * <p>This test validates that the application entry point is structurally
 * sound. Full context-loading tests will be added once the database schema
 * and security configuration are in place (Prompt 3+).</p>
 */
class EventOpsApplicationTests {

    @Test
    void applicationClassExists() {
        EventOpsApplication app = new EventOpsApplication();
        assertNotNull(app, "EventOpsApplication should be instantiable");
    }

    @Test
    void mainMethodExists() throws NoSuchMethodException {
        assertNotNull(
            EventOpsApplication.class.getMethod("main", String[].class),
            "main method should exist"
        );
    }
}
