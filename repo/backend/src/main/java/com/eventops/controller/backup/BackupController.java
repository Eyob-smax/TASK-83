package com.eventops.controller.backup;

import com.eventops.common.dto.ApiResponse;
import com.eventops.domain.backup.BackupJob;
import com.eventops.domain.backup.BackupStatus;
import com.eventops.security.auth.EventOpsUserDetails;
import com.eventops.service.backup.BackupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for database backup administration: listing backups,
 * triggering manual backups, and viewing retention status.
 *
 * <p>All endpoints return the standard {@link ApiResponse} envelope.</p>
 */
@RestController
@RequestMapping("/api/admin/backups")
public class BackupController {

    private static final Logger log = LoggerFactory.getLogger(BackupController.class);

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    /**
     * Lists all backup jobs ordered by creation time descending.
     *
     * @return 200 with list of backup jobs
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<BackupJob>>> listBackups() {
        log.debug("GET /api/admin/backups");

        List<BackupJob> backups = backupService.listBackups();
        return ResponseEntity.ok(ApiResponse.success(backups));
    }

    /**
     * Retrieves a single backup job by ID.
     *
     * @param id the backup job identifier
     * @return 200 with backup job data
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BackupJob>> getBackup(@PathVariable String id) {
        log.debug("GET /api/admin/backups/{}", id);

        BackupJob backup = backupService.getBackup(id);
        return ResponseEntity.ok(ApiResponse.success(backup));
    }

    /**
     * Triggers a manual backup execution.
     *
     * @param principal the authenticated user
     * @return 201 with the created backup job
     */
    @PostMapping("/trigger")
    public ResponseEntity<ApiResponse<BackupJob>> triggerBackup(
            @AuthenticationPrincipal EventOpsUserDetails principal) {
        String userId = principal.getUser().getId();
        log.debug("POST /api/admin/backups/trigger – userId={}", userId);

        BackupJob backup = backupService.triggerManualBackup(userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(backup, "Backup triggered"));
    }

    /**
     * Returns retention status metrics computed from existing backup jobs,
     * including total count, completed count, expired count, and retention
     * configuration.
     *
     * @return 200 with retention status map
     */
    @GetMapping("/retention")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRetentionStatus() {
        log.debug("GET /api/admin/backups/retention");

        List<BackupJob> backups = backupService.listBackups();
        Instant now = Instant.now();

        long total = backups.size();
        long completed = backups.stream()
                .filter(b -> b.getStatus() == BackupStatus.COMPLETED)
                .count();
        long expired = backups.stream()
                .filter(b -> b.getRetentionExpiresAt() != null && b.getRetentionExpiresAt().isBefore(now))
                .count();
        long failed = backups.stream()
                .filter(b -> b.getStatus() == BackupStatus.FAILED)
                .count();
        long inProgress = backups.stream()
                .filter(b -> b.getStatus() == BackupStatus.IN_PROGRESS)
                .count();

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("totalBackups", total);
        status.put("completedCount", completed);
        status.put("expiredCount", expired);
        status.put("failedCount", failed);
        status.put("inProgressCount", inProgress);
        status.put("retentionDays", backupService.getRetentionDays());

        return ResponseEntity.ok(ApiResponse.success(status));
    }
}
