package com.eventops.service.event;

import com.eventops.common.dto.event.EventSessionResponse;
import com.eventops.common.exception.BusinessException;
import com.eventops.domain.event.EventSession;
import com.eventops.domain.event.SessionStatus;
import com.eventops.repository.event.EventSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only service for browsing event sessions, searching, and checking availability.
 */
@Service
@Transactional(readOnly = true)
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventSessionRepository eventSessionRepository;

    public EventService(EventSessionRepository eventSessionRepository) {
        this.eventSessionRepository = eventSessionRepository;
    }

    /**
     * Lists sessions with optional filtering by status and title search.
     *
     * @param pageable pagination and sorting parameters
     * @param status   optional session status filter (exact match)
     * @param search   optional title search term (case-insensitive LIKE)
     * @return a page of session DTOs
     */
    public Page<EventSessionResponse> listSessions(Pageable pageable, String status, String search) {
        log.debug("Listing sessions – status={}, search={}, page={}", status, search, pageable);

        Specification<EventSession> spec = Specification.where(null);

        if (status != null && !status.isBlank()) {
            SessionStatus sessionStatus = SessionStatus.valueOf(status.toUpperCase());
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), sessionStatus));
        }

        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("title")), pattern));
        }

        Page<EventSession> page = eventSessionRepository.findAll(spec, pageable);
        log.debug("Found {} sessions (page {} of {})", page.getNumberOfElements(),
                page.getNumber(), page.getTotalPages());

        return page.map(this::mapToResponse);
    }

    /**
     * Retrieves a single session by ID.
     *
     * @param sessionId the session identifier
     * @return the session DTO
     * @throws BusinessException if the session does not exist
     */
    public EventSessionResponse getSession(String sessionId) {
        log.debug("Fetching session {}", sessionId);

        EventSession session = eventSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException("Session not found", 404, "NOT_FOUND"));

        return mapToResponse(session);
    }

    /**
     * Returns availability information for a session.
     * Remaining seats are computed via the entity's {@code @Transient} method.
     *
     * @param sessionId the session identifier
     * @return the session DTO (includes remainingSeats)
     * @throws BusinessException if the session does not exist
     */
    public EventSessionResponse getAvailability(String sessionId) {
        log.debug("Fetching availability for session {}", sessionId);

        EventSession session = eventSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException("Session not found", 404, "NOT_FOUND"));

        return mapToResponse(session);
    }

    // ---- internal helpers ----

    private EventSessionResponse mapToResponse(EventSession session) {
        EventSessionResponse dto = new EventSessionResponse();
        dto.setId(session.getId());
        dto.setTitle(session.getTitle());
        dto.setDescription(session.getDescription());
        dto.setLocation(session.getLocation());
        dto.setStartTime(session.getStartTime());
        dto.setEndTime(session.getEndTime());
        dto.setMaxCapacity(session.getMaxCapacity());
        dto.setCurrentRegistrations(session.getCurrentRegistrations());
        dto.setRemainingSeats(session.getRemainingSeats());
        dto.setStatus(session.getStatus().name());
        dto.setDeviceBindingRequired(session.isDeviceBindingRequired());
        dto.setWaitlistPromotionCutoff(session.getWaitlistPromotionCutoff());
        dto.setCreatedAt(session.getCreatedAt());
        return dto;
    }
}
