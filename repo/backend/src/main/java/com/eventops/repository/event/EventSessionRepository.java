package com.eventops.repository.event;

import com.eventops.domain.event.EventSession;
import com.eventops.domain.event.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface EventSessionRepository extends JpaRepository<EventSession, String>, JpaSpecificationExecutor<EventSession> {
    List<EventSession> findByStatus(SessionStatus status);

    @Query("SELECT e FROM EventSession e WHERE e.status IN :statuses AND e.startTime >= :from AND e.startTime <= :to")
    List<EventSession> findByStatusInAndStartTimeBetween(@Param("statuses") List<SessionStatus> statuses,
                                                          @Param("from") LocalDateTime from,
                                                          @Param("to") LocalDateTime to);

    @Query("SELECT e FROM EventSession e WHERE e.status = 'IN_PROGRESS' OR (e.status = 'OPEN_FOR_REGISTRATION' AND e.startTime <= :threshold)")
    List<EventSession> findActiveSessionsForCheckin(@Param("threshold") LocalDateTime threshold);
}
