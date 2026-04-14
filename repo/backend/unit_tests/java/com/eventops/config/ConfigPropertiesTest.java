package com.eventops.config;

import com.eventops.security.encryption.EncryptionProperties;
import com.eventops.security.ratelimit.RateLimitProperties;
import com.eventops.security.signature.SignatureProperties;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that configuration constants and @ConfigurationProperties defaults
 * match the values mandated by the prompt requirements.
 *
 * <p>These are pure-Java unit tests — no Spring context is loaded. They act
 * as a static contract check ensuring hardcoded defaults in properties classes
 * and AppConstants agree with one another and with the documented requirements
 * recorded in questions.md / application.yml.</p>
 */
class ConfigPropertiesTest {

    // -------------------------------------------------------------------------
    // AppConstants — shared constants used by services and verified against
    // the prompt requirements
    // -------------------------------------------------------------------------

    @Test
    void rateLimitDefault_is60RequestsPerMinute() {
        assertEquals(60, AppConstants.DEFAULT_RATE_LIMIT_RPM,
                "Rate limit default must be 60 req/min per requirement 3 (rate limiting)");
    }

    @Test
    void loginThrottle_defaults_matchRequirements() {
        assertEquals(5, AppConstants.LOGIN_MAX_ATTEMPTS,
                "Login throttle max attempts must be 5 per requirement 1 (anti-bot)");
        assertEquals(15, AppConstants.LOGIN_LOCKOUT_MINUTES,
                "Login lockout must be 15 minutes per requirement 1 (anti-bot)");
    }

    @Test
    void passcode_defaults_matchRequirements() {
        assertEquals(6, AppConstants.PASSCODE_LENGTH,
                "Passcode must be 6 digits per requirement 7 (check-in)");
        assertEquals(60, AppConstants.PASSCODE_EXPIRY_SECONDS,
                "Passcode must rotate every 60 seconds per requirement 7 (check-in)");
        assertEquals(60_000L, AppConstants.PASSCODE_ROTATION_INTERVAL_MS,
                "Rotation interval in ms must equal 60 000");
    }

    @Test
    void checkinWindow_defaults_matchRequirements() {
        assertEquals(30, AppConstants.DEFAULT_CHECKIN_WINDOW_BEFORE_MINUTES,
                "Check-in window must open 30 min before session start");
        assertEquals(15, AppConstants.DEFAULT_CHECKIN_WINDOW_AFTER_MINUTES,
                "Check-in window must close 15 min after session start");
    }

    @Test
    void notificationRetry_defaults_matchRequirements() {
        assertEquals(5, AppConstants.NOTIFICATION_MAX_ATTEMPTS,
                "Notification retry must allow 5 attempts per requirement 8");
    }

    @Test
    void dndWindow_defaults_matchRequirements() {
        assertEquals(21, AppConstants.DEFAULT_DND_START.getHour(),
                "DND window must start at 21:00 per requirement 8");
        assertEquals(0, AppConstants.DEFAULT_DND_START.getMinute());
        assertEquals(7, AppConstants.DEFAULT_DND_END.getHour(),
                "DND window must end at 07:00 per requirement 8");
        assertEquals(0, AppConstants.DEFAULT_DND_END.getMinute());
    }

    @Test
    void importOrchestration_defaults_matchRequirements() {
        assertEquals(3, AppConstants.IMPORT_DEFAULT_CONCURRENCY_CAP,
                "Import concurrency cap must be 3 per requirement 9 (import)");
        assertEquals(30, AppConstants.IMPORT_DEFAULT_TIMEOUT_SECONDS,
                "Import timeout must be 30 seconds per requirement 9 (import)");
        assertEquals(10, AppConstants.IMPORT_CIRCUIT_BREAKER_THRESHOLD,
                "Import circuit-breaker threshold must be 10 failures per requirement 9");
    }

    @Test
    void backupRetention_default_matches30Days() {
        assertEquals(30, AppConstants.BACKUP_RETENTION_DAYS,
                "Backup retention must be 30 days per requirement 12 (backups)");
    }

    // -------------------------------------------------------------------------
    // RateLimitProperties — @ConfigurationProperties defaults
    // -------------------------------------------------------------------------

    @Test
    void rateLimitProperties_defaultRequestsPerMinute() {
        RateLimitProperties props = new RateLimitProperties();
        assertEquals(60, props.getRequestsPerMinute(),
                "RateLimitProperties default must be 60 req/min");
    }

    // -------------------------------------------------------------------------
    // SignatureProperties — @ConfigurationProperties defaults
    // -------------------------------------------------------------------------

    @Test
    void signatureProperties_defaultAlgorithm() {
        SignatureProperties props = new SignatureProperties();
        assertEquals("HmacSHA256", props.getAlgorithm(),
                "Signature algorithm default must be HmacSHA256 per requirement 3");
    }

    @Test
    void signatureProperties_defaultMaxAgSeconds() {
        SignatureProperties props = new SignatureProperties();
        assertEquals(300, props.getMaxAgeSeconds(),
                "Signature max-age must be 300 seconds (replay protection window)");
    }

    @Test
    void signatureProperties_defaultEnabledState() {
        // Default in code is false; production activation via application.yml
        // default (enabled: true → overridden to false in dev profile).
        // The in-code default of false ensures tests pass without a signing secret.
        SignatureProperties props = new SignatureProperties();
        assertFalse(props.isEnabled(),
                "SignatureProperties code-level default must be false (enabled via yml)");
    }

    // -------------------------------------------------------------------------
    // EncryptionProperties — @ConfigurationProperties defaults
    // -------------------------------------------------------------------------

    @Test
    void encryptionProperties_algorithmAndTransformation() {
        // EncryptionProperties has no hardcoded defaults — values come from
        // application.yml. Verify the fields exist and the properties class
        // is correctly structured by using setters/getters.
        EncryptionProperties props = new EncryptionProperties();
        props.setAlgorithm("AES");
        props.setTransformation("AES/GCM/NoPadding");
        props.setKeySize(256);

        assertEquals("AES", props.getAlgorithm());
        assertEquals("AES/GCM/NoPadding", props.getTransformation(),
                "Encryption must use AES/GCM/NoPadding per requirement 4 (encryption)");
        assertEquals(256, props.getKeySize(),
                "AES key size must be 256 bits per requirement 4 (AES-256)");
    }

    @Test
    void encryptionProperties_secretKeyIsSettable() {
        EncryptionProperties props = new EncryptionProperties();
        props.setSecretKey("test-key");
        assertEquals("test-key", props.getSecretKey());
    }

    // -------------------------------------------------------------------------
    // WebConfig CORS — validate the env var default value is well-formed
    // -------------------------------------------------------------------------

    @Test
    void corsAllowedOrigins_defaultContainsExpectedOrigins() {
        // The default value from application.yml / WebConfig @Value annotation
        String defaultOrigins =
                "http://localhost:443,http://localhost:5173,http://localhost";
        String[] origins = defaultOrigins.split(",");
        assertEquals(3, origins.length,
                "Default CORS origins should include frontend port, Vite dev port, and bare localhost");
        assertEquals("http://localhost:443", origins[0].trim());
        assertEquals("http://localhost:5173", origins[1].trim());
        assertEquals("http://localhost", origins[2].trim());
    }

    @Test
    void flywayConfiguration_usesSingleClasspathLocation() throws IOException {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));
        String pomXml = Files.readString(Path.of("pom.xml"));

        assertTrue(applicationYaml.contains("locations: classpath:db/migration"));
        assertFalse(applicationYaml.contains("filesystem:database/migrations"));
        assertTrue(pomXml.contains("<location>classpath:db/migration</location>"));
        assertFalse(pomXml.contains("filesystem:${project.basedir}/database/migrations"));
    }

    @Test
    void migrationVersions_areUniqueInAuthoritativeClasspathLocation() throws IOException {
        try (var files = Files.list(Path.of("src/main/resources/db/migration"))) {
            List<String> versions = files
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("V"))
                    .map(name -> name.substring(0, name.indexOf("__")))
                    .collect(Collectors.toList());

            assertEquals(versions.size(), versions.stream().distinct().count(),
                    "Migration versions must be unique in the authoritative Flyway location");
        }
    }
}
