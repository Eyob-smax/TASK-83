package com.eventops.controller.event;

import com.eventops.common.dto.ApiResponse;
import com.eventops.common.dto.PagedResponse;
import com.eventops.common.dto.event.EventSessionResponse;
import com.eventops.service.event.EventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for browsing event sessions, searching, and checking availability.
 *
 * <p>All endpoints return the standard {@link ApiResponse} envelope.</p>
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    private static final Logger log = LoggerFactory.getLogger(EventController.class);

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    /**
     * Lists event sessions with optional filtering by status and search term.
     *
     * @param status   optional session status filter
     * @param search   optional title search term
     * @param pageable pagination and sorting parameters
     * @return 200 with paginated session list
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<EventSessionResponse>>> listSessions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        log.debug("GET /api/events – status={}, search={}, page={}", status, search, pageable);

        Page<EventSessionResponse> page = eventService.listSessions(pageable, status, search);
        PagedResponse<EventSessionResponse> pagedResponse = new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );

        return ResponseEntity.ok(ApiResponse.success(pagedResponse));
    }

    /**
     * Retrieves a single event session by ID.
     *
     * @param id the session identifier
     * @return 200 with session data
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EventSessionResponse>> getSession(@PathVariable String id) {
        log.debug("GET /api/events/{}", id);

        EventSessionResponse session = eventService.getSession(id);
        return ResponseEntity.ok(ApiResponse.success(session));
    }

    /**
     * Returns availability information for a session.
     *
     * @param id the session identifier
     * @return 200 with availability data (includes remainingSeats)
     */
    @GetMapping("/{id}/availability")
    public ResponseEntity<ApiResponse<EventSessionResponse>> getAvailability(@PathVariable String id) {
        log.debug("GET /api/events/{}/availability", id);

        EventSessionResponse availability = eventService.getAvailability(id);
        return ResponseEntity.ok(ApiResponse.success(availability));
    }
}
