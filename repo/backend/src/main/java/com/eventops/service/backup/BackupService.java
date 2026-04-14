package com.eventops.service.backup;

import com.eventops.audit.logging.AuditService;
import com.eventops.common.exception.BusinessException;
import com.eventops.domain.audit.AuditActionType;
import com.eventops.domain.backup.BackupJob;
import com.eventops.domain.backup.BackupStatus;
import com.eventops.repository.backup.BackupJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Manages database backup lifecycle: execution via {@code mysqldump}, retention
 * enforcement, and status reporting.
 *
 * <p>On {@link #executeBackup(String)}, the service invokes {@code mysqldump} as
 * a child process and writes the dump to the configured backup path. On failure
 * the job is marked FAILED; on success it is marked COMPLETED with a 30-day
 * retention expiry. Expired artifacts are cleaned up by {@link #cleanupExpiredBackups()}.</p>
 */
@Service
@Transactional
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    private final BackupJobRepository backupJobRepository;
    private final AuditService auditService;

    @Value("${eventops.backup.target-path}")
    private String backupPath;

    @Value("${eventops.backup.retention-days}")
    private int retentionDays;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    public BackupService(BackupJobRepository backupJobRepository,
                         AuditService auditService) {
        this.backupJobRepository = backupJobRepository;
        this.auditService = auditService;
    }

    /**
     * Executes a backup job: creates the metadata record, invokes {@code mysqldump}
     * to write a SQL dump file, and marks the job COMPLETED on success or FAILED
     * on any I/O or process error.
     *
     * @param triggeredBy the user or system identifier that triggered the backup
     * @return the persisted backup job (COMPLETED or FAILED)
     */
    public BackupJob executeBackup(String triggeredBy) {
        BackupJob job = new BackupJob();
        job.setStatus(BackupStatus.IN_PROGRESS);
        job.setStartedAt(Instant.now());
        job.setTriggeredBy(triggeredBy);

        String fileName = "eventops_backup_" + LocalDate.now() + "_" + System.currentTimeMillis() + ".sql";
        String filePath = backupPath + "/" + fileName;
        job.setFilePath(filePath);

        BackupJob saved = backupJobRepository.save(job);

        try {
            performDump(saved);
            saved.setStatus(BackupStatus.COMPLETED);
            saved.setCompletedAt(Instant.now());
            saved.setRetentionExpiresAt(Instant.now().plus(retentionDays, ChronoUnit.DAYS));

            Path dumpFile = Paths.get(filePath);
            if (Files.exists(dumpFile)) {
                saved.setFileSizeBytes(Files.size(dumpFile));
            }

            backupJobRepository.save(saved);

            auditService.log(AuditActionType.BACKUP_EXECUTED,
                    triggeredBy, triggeredBy, "SYSTEM",
                    "BackupJob", saved.getId(),
                    "Backup completed: path=" + filePath + ", triggeredBy=" + triggeredBy);

            log.info("Backup completed: id={}, path={}, triggeredBy={}", saved.getId(), filePath, triggeredBy);

        } catch (Exception e) {
            log.error("Backup failed: id={}, path={}, error={}", saved.getId(), filePath, e.getMessage(), e);
            saved.setStatus(BackupStatus.FAILED);
            saved.setCompletedAt(Instant.now());
            saved.setErrorMessage("Backup failed: " + e.getMessage());
            backupJobRepository.save(saved);

            auditService.log(AuditActionType.BACKUP_EXECUTED,
                    triggeredBy, triggeredBy, "SYSTEM",
                    "BackupJob", saved.getId(),
                    "Backup failed: " + e.getMessage());
        }

        return saved;
    }

    /**
     * Invokes {@code mysqldump} via a child process and redirects stdout to the
     * backup file path already set on {@code job}. Waits up to 5 minutes.
     *
     * @param job the backup job (must have filePath set)
     * @throws IOException          on process creation or file I/O errors
     * @throws InterruptedException if the waiting thread is interrupted
     */
    private void performDump(BackupJob job) throws IOException, InterruptedException {
        // Parse JDBC URL: jdbc:mysql://host:port/dbname?params
        String withoutPrefix = datasourceUrl.replace("jdbc:mysql://", "");
        String[] hostAndRest = withoutPrefix.split("/", 2);
        String hostPort = hostAndRest[0];
        String dbName = hostAndRest.length > 1 ? hostAndRest[1].split("\\?")[0] : "eventops";
        String host = hostPort.contains(":") ? hostPort.split(":")[0] : hostPort;
        String port = hostPort.contains(":") ? hostPort.split(":")[1] : "3306";

        Path outFile = Paths.get(job.getFilePath());
        Files.createDirectories(outFile.getParent());

        ProcessBuilder pb = new ProcessBuilder(
                "mysqldump",
                "--host=" + host,
                "--port=" + port,
                "--user=" + dbUsername,
                "--password=" + dbPassword,
                dbName
        );
        pb.redirectOutput(outFile.toFile());
        pb.redirectErrorStream(false);

        Process proc = pb.start();
        boolean finished = proc.waitFor(5, TimeUnit.MINUTES);

        if (!finished) {
            proc.destroyForcibly();
            throw new IOException("mysqldump timed out after 5 minutes");
        }
        int exitCode = proc.exitValue();
        if (exitCode != 0) {
            throw new IOException("mysqldump exited with code " + exitCode);
        }
    }

    /**
     * Cleans up expired backup artifacts. Queries for backup jobs whose retention
     * period has elapsed, attempts to delete the backup file from disk, and
     * updates or removes the metadata record.
     */
    public void cleanupExpiredBackups() {
        List<BackupJob> expiredBackups = backupJobRepository.findExpiredBackups(Instant.now());

        int deletedCount = 0;
        for (BackupJob job : expiredBackups) {
            // Attempt to delete the file at filePath
            if (job.getFilePath() != null) {
                try {
                    Path path = Paths.get(job.getFilePath());
                    Files.deleteIfExists(path);
                    log.debug("Deleted expired backup file: {}", job.getFilePath());
                } catch (IOException e) {
                    log.warn("Failed to delete expired backup file: {}, reason: {}",
                            job.getFilePath(), e.getMessage());
                }
            }

            // Update status to indicate retention expiry (distinct from FAILED)
            job.setStatus(BackupStatus.EXPIRED);
            job.setErrorMessage("Retention period elapsed — artifact removed");
            backupJobRepository.save(job);
            deletedCount++;
        }

        if (deletedCount > 0) {
            auditService.log(AuditActionType.BACKUP_RETENTION_CLEANUP,
                    "SYSTEM", "BackupService", "SYSTEM",
                    "BackupJob", null,
                    "Backup retention cleanup: " + deletedCount + " expired backup(s) removed");

            log.info("Backup retention cleanup completed: {} expired backup(s) removed", deletedCount);
        } else {
            log.debug("Backup retention cleanup: no expired backups found");
        }
    }

    /**
     * Returns all backup jobs ordered by creation time descending.
     *
     * @return list of backup jobs
     */
    @Transactional(readOnly = true)
    public List<BackupJob> listBackups() {
        return backupJobRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    /**
     * Retrieves a single backup job by ID.
     *
     * @param id the backup job identifier
     * @return the backup job
     * @throws BusinessException if the backup job does not exist
     */
    @Transactional(readOnly = true)
    public BackupJob getBackup(String id) {
        return backupJobRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Backup job not found", 404, "NOT_FOUND"));
    }

    /**
     * Triggers a manual backup for the specified user. Delegates to
     * {@link #executeBackup(String)}.
     *
     * @param userId the user triggering the backup
     * @return the completed backup job
     */
    public BackupJob triggerManualBackup(String userId) {
        return executeBackup(userId);
    }

    @Transactional(readOnly = true)
    public int getRetentionDays() {
        return retentionDays;
    }
}
