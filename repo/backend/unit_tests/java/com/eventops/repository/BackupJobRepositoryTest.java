package com.eventops.repository;

import com.eventops.domain.backup.BackupJob;
import com.eventops.domain.backup.BackupStatus;
import com.eventops.repository.backup.BackupJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class BackupJobRepositoryTest {

    @Autowired
    private BackupJobRepository backupJobRepository;

    @BeforeEach
    void clean() {
        backupJobRepository.deleteAll();
    }

    private BackupJob build(BackupStatus status, Instant retentionExpiresAt) {
        BackupJob b = new BackupJob();
        b.setStatus(status);
        b.setTriggeredBy("SCHEDULER");
        b.setFilePath("/backups/test.bak");
        b.setRetentionExpiresAt(retentionExpiresAt);
        return b;
    }

    @Test
    void saveAndFindById_roundTrip() {
        BackupJob saved = backupJobRepository.save(build(BackupStatus.SCHEDULED, null));
        Optional<BackupJob> found = backupJobRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals(BackupStatus.SCHEDULED, found.get().getStatus());
    }

    @Test
    void findByStatus_filtersCorrectly() {
        backupJobRepository.save(build(BackupStatus.COMPLETED, Instant.now().plus(7, ChronoUnit.DAYS)));
        backupJobRepository.save(build(BackupStatus.SCHEDULED, null));
        backupJobRepository.save(build(BackupStatus.COMPLETED, Instant.now().plus(7, ChronoUnit.DAYS)));
        backupJobRepository.save(build(BackupStatus.FAILED, null));

        assertEquals(2, backupJobRepository.findByStatus(BackupStatus.COMPLETED).size());
        assertEquals(1, backupJobRepository.findByStatus(BackupStatus.SCHEDULED).size());
        assertEquals(1, backupJobRepository.findByStatus(BackupStatus.FAILED).size());
        assertEquals(0, backupJobRepository.findByStatus(BackupStatus.IN_PROGRESS).size());
    }

    @Test
    void findExpiredBackups_returnsOnlyCompletedAndExpired() {
        Instant now = Instant.now();
        backupJobRepository.save(build(BackupStatus.COMPLETED, now.minus(1, ChronoUnit.DAYS)));
        backupJobRepository.save(build(BackupStatus.COMPLETED, now.minus(5, ChronoUnit.DAYS)));
        backupJobRepository.save(build(BackupStatus.COMPLETED, now.plus(3, ChronoUnit.DAYS)));
        backupJobRepository.save(build(BackupStatus.FAILED, now.minus(10, ChronoUnit.DAYS)));

        List<BackupJob> expired = backupJobRepository.findExpiredBackups(now);
        assertEquals(2, expired.size());
    }

    @Test
    void findExpiredBackups_emptyWhenNoExpired() {
        backupJobRepository.save(build(BackupStatus.COMPLETED, Instant.now().plus(7, ChronoUnit.DAYS)));
        List<BackupJob> expired = backupJobRepository.findExpiredBackups(Instant.now());
        assertEquals(0, expired.size());
    }
}
