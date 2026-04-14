package com.eventops.repository.checkin;

import com.eventops.domain.checkin.CheckInRecord;
import com.eventops.domain.checkin.CheckInStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface CheckInRecordRepository extends JpaRepository<CheckInRecord, String> {
    List<CheckInRecord> findBySessionId(String sessionId);
    List<CheckInRecord> findBySessionIdAndUserId(String sessionId, String userId);
    boolean existsBySessionIdAndUserIdAndStatus(String sessionId, String userId, CheckInStatus status);
    List<CheckInRecord> findBySessionIdAndStatus(String sessionId, CheckInStatus status);
    Optional<CheckInRecord> findTopByUserIdAndStatusAndSessionIdNotOrderByCheckedInAtDesc(
            String userId,
            CheckInStatus status,
            String sessionId);
}
