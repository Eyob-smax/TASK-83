package com.eventops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the EventOps Backend — Compliance-Grade Event Operations
 * and Accounting Platform.
 *
 * <p>Runs entirely on a local/offline facility network with no external
 * service dependencies. Serves REST APIs over HTTPS with request-signature
 * verification for the Vue.js frontend client.</p>
 */
@SpringBootApplication
@EnableScheduling
public class EventOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventOpsApplication.class, args);
    }
}
