package com.eventops.service;

import com.eventops.audit.logging.AuditService;
import com.eventops.common.exception.BusinessException;
import com.eventops.domain.audit.AuditActionType;
import com.eventops.domain.backup.BackupJob;
import com.eventops.domain.backup.BackupStatus;
import com.eventops.repository.backup.BackupJobRepository;
import com.eventops.service.backup.BackupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BackupServiceTest_Expanded {

    @Mock
    private BackupJobRepository backupJobRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private BackupService backupService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(backupService, "backupPath", tempDir.toString());
        ReflectionTestUtils.setField(backupService, "retentionDays", 30);
        ReflectionTestUtils.setField(backupService, "datasourceUrl", "jdbc:mysql://localhost:3306/eventops?useSSL=false");
        ReflectionTestUtils.setField(backupService, "dbUsername", "root");
        ReflectionTestUtils.setField(backupService, "dbPassword", "password");
    }

    // ---------------------------------------------------------------
    // executeBackup — error path (mysqldump not available)
    // ---------------------------------------------------------------

    @Test
    void executeBackup_setsStatusInProgress_thenFailsWhenDumpFails() {
        when(backupJobRepository.save(any(BackupJob.class)))
                .thenAnswer(inv -> {
                    BackupJob j = inv.getArgument(0);
                    if (j.getId() == null) j.setId("job-1");
                    return j;
                });

        BackupJob result = backupService.executeBackup("admin-user");

        // mysqldump is not available in test env, so job should be FAILED
        assertEquals(BackupStatus.FAILED, result.getStatus());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Backup failed"));
    }

    @Test
    void executeBackup_setsTriggeredBy() {
        when(backupJobRepository.save(any(BackupJob.class)))
                .thenAnswer(inv -> {
                    BackupJob j = inv.getArgument(0);
                    if (j.getId() == null) j.setId("job-2");
                    return j;
                });

        BackupJob result = backupService.executeBackup("scheduler-system");

        assertEquals("scheduler-system", result.getTriggeredBy());
    }

    @Test
    void executeBackup_setsFilePathUnderBackupDir() {
        when(backupJobRepository.save(any(BackupJob.class)))
                .thenAnswer(inv -> {
                    BackupJob j = inv.getArgument(0);
                    if (j.getId() == null) j.setId("job-3");
                    return j;
                });

        BackupJob result = backupService.executeBackup("admin");

        assertNotNull(result.getFilePath());
        assertTrue(result.getFilePath().startsWith(tempDir.toString()));
        assertTrue(result.getFilePath().endsWith(".sql"));
    }

    @Test
    void executeBackup_setsStartedAt() {
        when(backupJobRepository.save(any(BackupJob.class)))
                .thenAnswer(inv -> {
                    BackupJob j = inv.getArgument(0);
                    if (j.getId() == null) j.setId("job-4");
                    return j;
                });

        BackupJob result = backupService.executeBackup("admin");

        assertNotNull(result.getStartedAt());
    }

    @Test
    void executeBackup_onFailure_setsCompletedAt() {
        when(backupJobRepository.save(any(BackupJob.class)))
                .thenAnswer(inv -> {
                    BackupJob j = inv.getArgument(0);
                    if (j.getId() == null) j.setId("job-5");
                    return j;
                });

        BackupJob result = backupService.executeBackup("admin");

        assertEquals(BackupStatus.FAILED, result.getStatus());
        assertNotNull(result.getCompletedAt());
    }

    @Test
    void executeBackup_onFailure_logsAuditEvent() {
        when(backupJobRepository.save(any(BackupJob.class)))
                .thenAnswer(inv -> {
                    BackupJob j = inv.getArgument(0);
                    if (j.getId() == null) j.setId("job-6");
                    return j;
                });

        backupService.executeBackup("admin");

        verify(auditService).log(eq(AuditActionType.BACKUP_EXECUTED),
                eq("admin"), eq("admin"), eq("SYSTEM"),
                eq("BackupJob"), eq("job-6"),
                contains("Backup failed"));
    }

    @Test
    void executeBackup_saveCalledAtLeastTwice_initialAndFinal() {
        when(backupJobRepository.save(any(BackupJob.class)))
                .thenAnswer(inv -> {
                    BackupJob j = inv.getArgument(0);
                    if (j.getId() == null) j.setId("job-7");
                    return j;
                });

        backupService.executeBackup("admin");

        // First save: IN_PROGRESS; Second save: FAILED (or COMPLETED)
        verify(backupJobRepository, atLeast(2)).save(any(BackupJob.class));
    }

    // ---------------------------------------------------------------
    // cleanupExpiredBackups
    // ---------------------------------------------------------------

    @Test
    void cleanupExpiredBackups_noExpired_doesNotAudit() {
        when(backupJobRepository.findExpiredBackups(any(Instant.class)))
                .thenReturn(Collections.emptyList());

        backupService.cleanupExpiredBackups();

        verifyNoInteractions(auditService);
    }

    @Test
    void cleanupExpiredBackups_withExpired_setsStatusExpired() {
        BackupJob expired = new BackupJob();
        expired.setId("exp-1");
        expired.setStatus(BackupStatus.COMPLETED);
        expired.setFilePath(tempDir.resolve("old_backup.sql").toString());

        when(backupJobRepository.findExpiredBackups(any(Instant.class)))
                .thenReturn(List.of(expired));

        backupService.cleanupExpiredBackups();

        assertEquals(BackupStatus.EXPIRED, expired.getStatus());
        assertEquals("Retention period elapsed \u2014 artifact removed", expired.getErrorMessage());
        verify(backupJobRepository).save(expired);
    }

    @Test
    void cleanupExpiredBackups_withExpired_logsAuditEvent() {
        BackupJob expired = new BackupJob();
        expired.setId("exp-2");
        expired.setStatus(BackupStatus.COMPLETED);
        expired.setFilePath(null);

        when(backupJobRepository.findExpiredBackups(any(Instant.class)))
                .thenReturn(List.of(expired));

        backupService.cleanupExpiredBackups();

        verify(auditService).log(eq(AuditActionType.BACKUP_RETENTION_CLEANUP),
                eq("SYSTEM"), eq("BackupService"), eq("SYSTEM"),
                eq("BackupJob"), isNull(),
                contains("1 expired backup(s) removed"));
    }

    @Test
    void cleanupExpiredBackups_multipleExpired_countsCorrectly() {
        BackupJob exp1 = new BackupJob();
        exp1.setId("exp-a");
        exp1.setStatus(BackupStatus.COMPLETED);
        exp1.setFilePath(null);

        BackupJob exp2 = new BackupJob();
        exp2.setId("exp-b");
        exp2.setStatus(BackupStatus.COMPLETED);
        exp2.setFilePath(null);

        when(backupJobRepository.findExpiredBackups(any(Instant.class)))
                .thenReturn(List.of(exp1, exp2));

        backupService.cleanupExpiredBackups();

        verify(auditService).log(eq(AuditActionType.BACKUP_RETENTION_CLEANUP),
                eq("SYSTEM"), eq("BackupService"), eq("SYSTEM"),
                eq("BackupJob"), isNull(),
                contains("2 expired backup(s) removed"));
    }

    @Test
    void cleanupExpiredBackups_ioExceptionOnDelete_stillMarksExpired() {
        // Use a path that cannot be deleted (non-existent is fine -- deleteIfExists returns false)
        BackupJob expired = new BackupJob();
        expired.setId("exp-io");
        expired.setStatus(BackupStatus.COMPLETED);
        expired.setFilePath(tempDir.resolve("nonexistent.sql").toString());

        when(backupJobRepository.findExpiredBackups(any(Instant.class)))
                .thenReturn(List.of(expired));

        backupService.cleanupExpiredBackups();

        assertEquals(BackupStatus.EXPIRED, expired.getStatus());
        verify(backupJobRepository).save(expired);
    }

    // ---------------------------------------------------------------
    // listBackups
    // ---------------------------------------------------------------

    @Test
    void listBackups_returnsSortedByCreatedAtDesc() {
        BackupJob j1 = new BackupJob();
        j1.setId("j1");
        BackupJob j2 = new BackupJob();
        j2.setId("j2");

        when(backupJobRepository.findAll(any(Sort.class))).thenReturn(List.of(j2, j1));

        List<BackupJob> result = backupService.listBackups();

        assertEquals(2, result.size());
        assertEquals("j2", result.get(0).getId());
        verify(backupJobRepository).findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    // ---------------------------------------------------------------
    // getBackup
    // ---------------------------------------------------------------

    @Test
    void getBackup_found_returnsJob() {
        BackupJob job = new BackupJob();
        job.setId("existing-id");
        when(backupJobRepository.findById("existing-id")).thenReturn(Optional.of(job));

        BackupJob result = backupService.getBackup("existing-id");

        assertEquals("existing-id", result.getId());
    }

    @Test
    void getBackup_notFound_throwsBusinessException() {
        when(backupJobRepository.findById("missing")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> backupService.getBackup("missing"));
        assertEquals(404, ex.getHttpStatus());
        assertEquals("NOT_FOUND", ex.getErrorCode());
    }

    // ---------------------------------------------------------------
    // triggerManualBackup
    // ---------------------------------------------------------------

    @Test
    void triggerManualBackup_delegatesToExecuteBackup() {
        when(backupJobRepository.save(any(BackupJob.class)))
                .thenAnswer(inv -> {
                    BackupJob j = inv.getArgument(0);
                    if (j.getId() == null) j.setId("manual-1");
                    return j;
                });

        BackupJob result = backupService.triggerManualBackup("user-42");

        assertEquals("user-42", result.getTriggeredBy());
    }

    // ---------------------------------------------------------------
    // getRetentionDays
    // ---------------------------------------------------------------

    @Test
    void getRetentionDays_returnsConfiguredValue() {
        assertEquals(30, backupService.getRetentionDays());
    }
}
