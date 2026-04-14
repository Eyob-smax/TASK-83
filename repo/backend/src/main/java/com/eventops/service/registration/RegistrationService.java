package com.eventops.service.registration;

import com.eventops.audit.logging.AuditService;
import com.eventops.common.dto.ConflictType;
import com.eventops.common.dto.registration.RegistrationResponse;
import com.eventops.common.exception.BusinessException;
import com.eventops.domain.audit.AuditActionType;
import com.eventops.domain.event.EventSession;
import com.eventops.domain.event.SessionStatus;
import com.eventops.domain.notification.NotificationType;
import com.eventops.domain.registration.Registration;
import com.eventops.domain.registration.RegistrationStatus;
import com.eventops.repository.event.EventSessionRepository;
import com.eventops.repository.registration.RegistrationRepository;
import com.eventops.service.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final RegistrationRepository registrationRepository;
    private final EventSessionRepository eventSessionRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public RegistrationService(RegistrationRepository registrationRepository,
                               EventSessionRepository eventSessionRepository,
                               AuditService auditService,
                               NotificationService notificationService) {
        this.registrationRepository = registrationRepository;
        this.eventSessionRepository = eventSessionRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    public RegistrationResponse register(String userId, String sessionId) {
        EventSession session = eventSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException("Session not found", 404, "NOT_FOUND"));

        // Validate session is open for registration
        if (session.getStatus() != SessionStatus.OPEN_FOR_REGISTRATION
                && session.getStatus() != SessionStatus.FULL) {
            throw new BusinessException("Session is not open for registration", 422, "SESSION_NOT_OPEN");
        }

        // Check duplicate registration
        if (registrationRepository.existsByUserIdAndSessionId(userId, sessionId)) {
            throw new BusinessException("Already registered for this session", 409,
                    "DUPLICATE_REGISTRATION", ConflictType.DUPLICATE_REGISTRATION);
        }

        Registration registration = new Registration();
        registration.setUserId(userId);
        registration.setSessionId(sessionId);

        long activeCount = registrationRepository.countActiveRegistrations(sessionId);

        if (activeCount < session.getMaxCapacity()) {
            // Has capacity — confirm registration
            registration.setStatus(RegistrationStatus.CONFIRMED);
            registrationRepository.save(registration);

            session.setCurrentRegistrations((int) activeCount + 1);
            if (session.getCurrentRegistrations() >= session.getMaxCapacity()) {
                session.setStatus(SessionStatus.FULL);
            }
            eventSessionRepository.save(session);

            auditService.logForCurrentUser(AuditActionType.REGISTRATION_CREATED,
                    "Registration", registration.getId(),
                    "Confirmed registration for session: " + session.getTitle());

            notificationService.sendNotification(userId,
                    NotificationType.REGISTRATION_CONFIRMATION, registration.getId(),
                    Map.of("sessionTitle", session.getTitle(), "status", "CONFIRMED"));

            log.info("Registration confirmed: user={}, session={}", userId, sessionId);
        } else {
            // Full — add to waitlist
            List<Registration> waitlisted = registrationRepository
                    .findWaitlistedBySessionOrderByPosition(sessionId);
            int nextPosition = waitlisted.isEmpty() ? 1 : waitlisted.size() + 1;

            registration.setStatus(RegistrationStatus.WAITLISTED);
            registration.setWaitlistPosition(nextPosition);
            registrationRepository.save(registration);

            auditService.logForCurrentUser(AuditActionType.WAITLIST_JOINED,
                    "Registration", registration.getId(),
                    "Waitlisted at position " + nextPosition + " for session: " + session.getTitle());

            log.info("Registration waitlisted: user={}, session={}, position={}",
                    userId, sessionId, nextPosition);
        }

        return mapToResponse(registration, session);
    }

    public RegistrationResponse cancel(String userId, String registrationId) {
        Registration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new BusinessException("Registration not found", 404, "NOT_FOUND"));

        if (!registration.getUserId().equals(userId)) {
            throw new BusinessException("Access denied", 403, "ACCESS_DENIED");
        }

        if (registration.getStatus() == RegistrationStatus.CANCELLED) {
            throw new BusinessException("Registration already cancelled", 422, "ALREADY_CANCELLED");
        }

        EventSession session = eventSessionRepository.findById(registration.getSessionId())
                .orElseThrow(() -> new BusinessException("Session not found", 404, "NOT_FOUND"));

        boolean wasActive = registration.getStatus() == RegistrationStatus.CONFIRMED
                || registration.getStatus() == RegistrationStatus.PROMOTED;

        registration.setStatus(RegistrationStatus.CANCELLED);
        registration.setCancelledAt(Instant.now());
        registrationRepository.save(registration);

        if (wasActive) {
            long activeCount = registrationRepository.countActiveRegistrations(session.getId());
            session.setCurrentRegistrations((int) activeCount);
            if (session.getStatus() == SessionStatus.FULL && activeCount < session.getMaxCapacity()) {
                session.setStatus(SessionStatus.OPEN_FOR_REGISTRATION);
            }
            eventSessionRepository.save(session);

            // Trigger waitlist promotion for freed seat
            promoteNextWaitlisted(session.getId());
        }

        auditService.logForCurrentUser(AuditActionType.REGISTRATION_CANCELLED,
                "Registration", registration.getId(),
                "Cancelled registration for session: " + session.getTitle());

        log.info("Registration cancelled: user={}, registration={}", userId, registrationId);
        return mapToResponse(registration, session);
    }

    @Transactional(readOnly = true)
    public List<RegistrationResponse> getUserRegistrations(String userId) {
        return registrationRepository.findByUserId(userId).stream()
                .map(reg -> {
                    EventSession session = eventSessionRepository.findById(reg.getSessionId()).orElse(null);
                    return mapToResponse(reg, session);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RegistrationResponse getRegistration(String registrationId, String userId) {
        Registration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new BusinessException("Registration not found", 404, "NOT_FOUND"));

        if (!registration.getUserId().equals(userId)) {
            throw new BusinessException("Access denied", 403, "ACCESS_DENIED");
        }

        EventSession session = eventSessionRepository.findById(registration.getSessionId()).orElse(null);
        return mapToResponse(registration, session);
    }

    /**
     * Returns all registrations for a session (for staff roster view).
     * Includes all statuses so staff can see confirmed, waitlisted, and cancelled entries.
     *
     * @param sessionId the session identifier
     * @return list of all registrations for the session
     */
    @Transactional(readOnly = true)
    public List<RegistrationResponse> getSessionRoster(String sessionId) {
        EventSession session = eventSessionRepository.findById(sessionId).orElse(null);
        return registrationRepository.findBySessionId(sessionId).stream()
                .map(reg -> mapToResponse(reg, session))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RegistrationResponse> getWaitlistPositions(String userId) {
        return registrationRepository.findByUserIdAndStatus(userId, RegistrationStatus.WAITLISTED).stream()
                .map(reg -> {
                    EventSession session = eventSessionRepository.findById(reg.getSessionId()).orElse(null);
                    return mapToResponse(reg, session);
                })
                .collect(Collectors.toList());
    }

    public void promoteNextWaitlisted(String sessionId) {
        EventSession session = eventSessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.isFull()) {
            return;
        }

        List<Registration> waitlisted = registrationRepository
                .findWaitlistedBySessionOrderByPosition(sessionId);

        if (waitlisted.isEmpty()) {
            return;
        }

        // Check promotion cutoff
        if (session.getWaitlistPromotionCutoff() != null
                && LocalDateTime.now().isAfter(session.getWaitlistPromotionCutoff())) {
            // Expire all remaining waitlisted entries
            for (Registration reg : waitlisted) {
                reg.setStatus(RegistrationStatus.EXPIRED);
                registrationRepository.save(reg);

                auditService.log(AuditActionType.WAITLIST_EXPIRED,
                        "SYSTEM", "WaitlistPromotionScheduler", "SCHEDULER",
                        "Registration", reg.getId(),
                        "Waitlist entry expired — promotion cutoff passed");

                notificationService.sendNotification(reg.getUserId(),
                        NotificationType.WAITLIST_EXPIRED, reg.getId(),
                        Map.of("sessionTitle", session.getTitle()));
            }
            log.info("Expired {} waitlist entries for session {} — cutoff passed", waitlisted.size(), sessionId);
            return;
        }

        // Promote the first waitlisted entry
        Registration promoted = waitlisted.get(0);
        promoted.setStatus(RegistrationStatus.PROMOTED);
        promoted.setPromotedAt(Instant.now());
        promoted.setWaitlistPosition(null);
        registrationRepository.save(promoted);

        long activeCount = registrationRepository.countActiveRegistrations(sessionId);
        session.setCurrentRegistrations((int) activeCount);
        if (session.getCurrentRegistrations() >= session.getMaxCapacity()) {
            session.setStatus(SessionStatus.FULL);
        }
        eventSessionRepository.save(session);

        auditService.log(AuditActionType.WAITLIST_PROMOTED,
                "SYSTEM", "WaitlistPromotionScheduler", "SCHEDULER",
                "Registration", promoted.getId(),
                "Promoted from waitlist for session: " + session.getTitle());

        notificationService.sendNotification(promoted.getUserId(),
                NotificationType.WAITLIST_PROMOTION, promoted.getId(),
                Map.of("sessionTitle", session.getTitle(), "status", "PROMOTED"));

        log.info("Promoted waitlist entry: user={}, session={}", promoted.getUserId(), sessionId);
    }

    private RegistrationResponse mapToResponse(Registration reg, EventSession session) {
        RegistrationResponse response = new RegistrationResponse();
        response.setId(reg.getId());
        response.setUserId(reg.getUserId());
        response.setSessionId(reg.getSessionId());
        response.setSessionTitle(session != null ? session.getTitle() : null);
        response.setStatus(reg.getStatus().name());
        response.setWaitlistPosition(reg.getWaitlistPosition());
        response.setPromotedAt(reg.getPromotedAt());
        response.setCreatedAt(reg.getCreatedAt());
        return response;
    }
}
