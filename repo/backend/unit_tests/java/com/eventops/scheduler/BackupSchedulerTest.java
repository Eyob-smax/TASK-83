package com.eventops.scheduler;

import com.eventops.service.backup.BackupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BackupSchedulerTest {

    @Mock BackupService backupService;
    @InjectMocks BackupScheduler scheduler;

    @Test
    void executeNightlyBackup_callsBackupService() {
        scheduler.executeNightlyBackup();
        verify(backupService).executeBackup("SCHEDULER");
    }

    @Test
    void executeNightlyBackup_swallowsExceptions() {
        doThrow(new RuntimeException("db down")).when(backupService).executeBackup(anyString());
        assertDoesNotThrow(scheduler::executeNightlyBackup);
    }

    @Test
    void cleanupExpiredBackups_callsBackupService() {
        scheduler.cleanupExpiredBackups();
        verify(backupService).cleanupExpiredBackups();
    }

    @Test
    void cleanupExpiredBackups_swallowsExceptions() {
        doThrow(new RuntimeException("fs error")).when(backupService).cleanupExpiredBackups();
        assertDoesNotThrow(scheduler::cleanupExpiredBackups);
    }
}
