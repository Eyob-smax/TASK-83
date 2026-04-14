package com.eventops.controller.notification;

import com.eventops.common.dto.ApiResponse;
import com.eventops.common.dto.PagedResponse;
import com.eventops.common.dto.notification.DndSettingsRequest;
import com.eventops.common.dto.notification.NotificationResponse;
import com.eventops.domain.notification.DndRule;
import com.eventops.domain.notification.Subscription;
import com.eventops.security.auth.EventOpsUserDetails;
import com.eventops.service.notification.NotificationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for notification management: listing notifications,
 * marking as read, managing subscriptions, and DND settings.
 *
 * <p>All endpoints return the standard {@link ApiResponse} envelope.</p>
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Lists delivered notifications for the current user with pagination.
     *
     * @param principal the authenticated user
     * @param pageable  pagination parameters
     * @return 200 with paginated notification list
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<NotificationResponse>>> list(
            @AuthenticationPrincipal EventOpsUserDetails principal,
            Pageable pageable) {
        String userId = principal.getUser().getId();
        log.debug("GET /api/notifications – userId={}, page={}", userId, pageable);

        Page<NotificationResponse> page = notificationService.getNotifications(userId, pageable);
        PagedResponse<NotificationResponse> pagedResponse = new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );

        return ResponseEntity.ok(ApiResponse.success(pagedResponse));
    }

    /**
     * Returns the count of unread notifications for the current user.
     *
     * @param principal the authenticated user
     * @return 200 with unread count
     */
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount(
            @AuthenticationPrincipal EventOpsUserDetails principal) {
        String userId = principal.getUser().getId();
        log.debug("GET /api/notifications/unread-count – userId={}", userId);

        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", count)));
    }

    /**
     * Marks a notification as read for the current user.
     *
     * @param id        the notification identifier
     * @param principal the authenticated user
     * @return 200 with success message
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable String id,
            @AuthenticationPrincipal EventOpsUserDetails principal) {
        String userId = principal.getUser().getId();
        log.debug("PATCH /api/notifications/{}/read – userId={}", id, userId);

        notificationService.markAsRead(id, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Notification marked as read"));
    }

    /**
     * Returns all notification subscriptions for the current user.
     *
     * @param principal the authenticated user
     * @return 200 with list of subscriptions
     */
    @GetMapping("/subscriptions")
    public ResponseEntity<ApiResponse<List<Subscription>>> getSubscriptions(
            @AuthenticationPrincipal EventOpsUserDetails principal) {
        String userId = principal.getUser().getId();
        log.debug("GET /api/notifications/subscriptions – userId={}", userId);

        List<Subscription> subscriptions = notificationService.getSubscriptions(userId);
        return ResponseEntity.ok(ApiResponse.success(subscriptions));
    }

    /**
     * Creates or updates notification subscriptions for the current user.
     *
     * @param subscriptions list of subscription updates
     * @param principal     the authenticated user
     * @return 200 with success message
     */
    @PutMapping("/subscriptions")
    public ResponseEntity<ApiResponse<Void>> updateSubscriptions(
            @RequestBody List<Map<String, Object>> subscriptions,
            @AuthenticationPrincipal EventOpsUserDetails principal) {
        String userId = principal.getUser().getId();
        log.debug("PUT /api/notifications/subscriptions – userId={}, count={}", userId, subscriptions.size());

        notificationService.updateSubscriptions(userId, subscriptions);
        return ResponseEntity.ok(ApiResponse.success(null, "Subscriptions updated"));
    }

    /**
     * Returns the DND (do-not-disturb) settings for the current user.
     *
     * @param principal the authenticated user
     * @return 200 with DND settings
     */
    @GetMapping("/dnd")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDndSettings(
            @AuthenticationPrincipal EventOpsUserDetails principal) {
        String userId = principal.getUser().getId();
        log.debug("GET /api/notifications/dnd – userId={}", userId);

        DndRule rule = notificationService.getDndSettings(userId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("startTime", rule.getStartTime().toString());
        response.put("endTime", rule.getEndTime().toString());
        response.put("enabled", rule.isEnabled());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Creates or updates the DND settings for the current user.
     *
     * @param request   the DND settings request
     * @param principal the authenticated user
     * @return 200 with updated DND settings
     */
    @PutMapping("/dnd")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateDndSettings(
            @Valid @RequestBody DndSettingsRequest request,
            @AuthenticationPrincipal EventOpsUserDetails principal) {
        String userId = principal.getUser().getId();
        log.debug("PUT /api/notifications/dnd – userId={}", userId);

        DndRule updated = notificationService.updateDndSettings(
                userId, request.getStartTime(), request.getEndTime(), request.isEnabled());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("startTime", updated.getStartTime().toString());
        response.put("endTime", updated.getEndTime().toString());
        response.put("enabled", updated.isEnabled());

        return ResponseEntity.ok(ApiResponse.success(response, "DND settings updated"));
    }
}
