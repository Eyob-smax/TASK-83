package com.eventops.scheduler;

import com.eventops.service.backup.BackupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Executes nightly database backups and enforces 30-day retention.
 */
@Component
public class BackupScheduler {

    private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);

    private final BackupService backupService;

    public BackupScheduler(BackupService backupService) {
        this.backupService = backupService;
    }

    /**
     * Nightly backup at 2:00 AM.
     */
    @Scheduled(cron = "${eventops.backup.cron:0 0 2 * * ?}")
    public void executeNightlyBackup() {
        log.info("Starting nightly backup job");
        try {
            backupService.executeBackup("SCHEDULER");
            log.info("Nightly backup completed successfully");
        } catch (Exception e) {
            log.error("Nightly backup failed: {}", e.getMessage());
        }
    }

    /**
     * Retention cleanup runs daily at 3:00 AM (1 hour after backup).
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredBackups() {
        log.info("Starting backup retention cleanup");
        try {
            backupService.cleanupExpiredBackups();
            log.info("Backup retention cleanup completed");
        } catch (Exception e) {
            log.error("Backup retention cleanup failed: {}", e.getMessage());
        }
    }
}
