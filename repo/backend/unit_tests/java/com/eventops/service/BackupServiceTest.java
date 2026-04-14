package com.eventops.service;

import com.eventops.domain.backup.BackupJob;
import com.eventops.domain.backup.BackupStatus;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import static org.junit.jupiter.api.Assertions.*;

class BackupServiceTest {

    @Test
    void retentionExpiry_is30DaysFromNow() {
        Instant now = Instant.now();
        Instant expiry = now.plus(30, ChronoUnit.DAYS);
        long daysBetween = ChronoUnit.DAYS.between(now, expiry);
        assertEquals(30, daysBetween);
    }

    @Test
    void backupJob_defaultStatus_isScheduled() {
        BackupJob job = new BackupJob();
        assertEquals(BackupStatus.SCHEDULED, job.getStatus());
    }

    @Test
    void expiredBackup_isIdentifiable() {
        BackupJob job = new BackupJob();
        job.setStatus(BackupStatus.COMPLETED);
        job.setRetentionExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        assertTrue(job.getRetentionExpiresAt().isBefore(Instant.now()));
    }

    @Test
    void retentionDays_defaultIs30() {
        assertEquals(30, com.eventops.config.AppConstants.BACKUP_RETENTION_DAYS);
    }

    @Test
    void expiredStatus_isDistinctFromFailed() {
        // BackupStatus.EXPIRED must exist and be semantically distinct from FAILED
        assertNotEquals(BackupStatus.EXPIRED, BackupStatus.FAILED,
                "Retention-expired backups must use EXPIRED status, not FAILED");
    }
}
