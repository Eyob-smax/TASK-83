package com.eventops.repository.backup;

import com.eventops.domain.backup.BackupJob;
import com.eventops.domain.backup.BackupStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface BackupJobRepository extends JpaRepository<BackupJob, String> {
    List<BackupJob> findByStatus(BackupStatus status);

    @Query("SELECT b FROM BackupJob b WHERE b.status = 'COMPLETED' AND b.retentionExpiresAt <= :now")
    List<BackupJob> findExpiredBackups(@Param("now") Instant now);
}
