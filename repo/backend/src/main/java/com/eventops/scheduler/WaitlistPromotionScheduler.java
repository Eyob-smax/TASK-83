package com.eventops.scheduler;

import com.eventops.domain.event.EventSession;
import com.eventops.domain.event.SessionStatus;
import com.eventops.repository.event.EventSessionRepository;
import com.eventops.service.registration.RegistrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Periodically reconciles waitlist positions against session capacity.
 * Runs every 5 minutes.
 */
@Component
public class WaitlistPromotionScheduler {

    private static final Logger log = LoggerFactory.getLogger(WaitlistPromotionScheduler.class);

    private final RegistrationService registrationService;
    private final EventSessionRepository eventSessionRepository;

    public WaitlistPromotionScheduler(RegistrationService registrationService,
                                       EventSessionRepository eventSessionRepository) {
        this.registrationService = registrationService;
        this.eventSessionRepository = eventSessionRepository;
    }

    @Scheduled(fixedRate = 300000)
    public void reconcileWaitlists() {
        List<EventSession> openSessions = eventSessionRepository
                .findByStatus(SessionStatus.OPEN_FOR_REGISTRATION);

        int promoted = 0;
        for (EventSession session : openSessions) {
            if (!session.isFull()) {
                try {
                    registrationService.promoteNextWaitlisted(session.getId());
                    promoted++;
                } catch (Exception e) {
                    log.error("Waitlist promotion failed for session {}: {}",
                            session.getId(), e.getMessage());
                }
            }
        }

        if (promoted > 0) {
            log.info("Waitlist reconciliation: processed {} sessions", promoted);
        }
    }
}
