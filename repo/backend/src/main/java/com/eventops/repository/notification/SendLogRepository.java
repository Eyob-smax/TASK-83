package com.eventops.repository.notification;

import com.eventops.domain.notification.SendLog;
import com.eventops.domain.notification.SendStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SendLogRepository extends JpaRepository<SendLog, String> {
    Optional<SendLog> findByIdempotencyKey(String idempotencyKey);
    boolean existsByIdempotencyKey(String idempotencyKey);
    List<SendLog> findByUserId(String userId);

    Page<SendLog> findByUserIdAndStatus(String userId, SendStatus status, Pageable pageable);

    long countByUserIdAndStatusAndReadAtIsNull(String userId, SendStatus status);

    @Query("SELECT s FROM SendLog s WHERE s.status = 'PENDING' OR (s.status = 'FAILED_RETRYING' AND s.nextAttemptAt <= :now) ORDER BY s.nextAttemptAt ASC")
    List<SendLog> findRetryableNotifications(@Param("now") Instant now);

    long countByStatus(SendStatus status);
}
