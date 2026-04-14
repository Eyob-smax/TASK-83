package com.eventops.repository.registration;

import com.eventops.domain.registration.Registration;
import com.eventops.domain.registration.RegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface RegistrationRepository extends JpaRepository<Registration, String> {
    boolean existsByUserIdAndSessionId(String userId, String sessionId);
    Optional<Registration> findByUserIdAndSessionId(String userId, String sessionId);
    List<Registration> findByUserId(String userId);
    List<Registration> findBySessionId(String sessionId);
    List<Registration> findBySessionIdAndStatus(String sessionId, RegistrationStatus status);

    @Query("SELECT r FROM Registration r WHERE r.sessionId = :sessionId AND r.status = 'WAITLISTED' ORDER BY r.waitlistPosition ASC")
    List<Registration> findWaitlistedBySessionOrderByPosition(@Param("sessionId") String sessionId);

    @Query("SELECT COUNT(r) FROM Registration r WHERE r.sessionId = :sessionId AND r.status IN ('CONFIRMED', 'PROMOTED')")
    long countActiveRegistrations(@Param("sessionId") String sessionId);

    List<Registration> findByUserIdAndStatus(String userId, RegistrationStatus status);
}
