package com.eventops.service.notification;

import com.eventops.audit.logging.AuditService;
import com.eventops.config.AppConstants;
import com.eventops.common.dto.notification.NotificationResponse;
import com.eventops.common.exception.BusinessException;
import com.eventops.domain.audit.AuditActionType;
import com.eventops.domain.notification.DndRule;
import com.eventops.domain.notification.NotificationTemplate;
import com.eventops.domain.notification.NotificationType;
import com.eventops.domain.notification.SendLog;
import com.eventops.domain.notification.SendStatus;
import com.eventops.domain.notification.Subscription;
import com.eventops.repository.notification.DndRuleRepository;
import com.eventops.repository.notification.NotificationTemplateRepository;
import com.eventops.repository.notification.SendLogRepository;
import com.eventops.repository.notification.SubscriptionRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Manages notification lifecycle: creation with idempotency, template rendering,
 * DND window enforcement, retry delivery with exponential backoff, and user
 * subscription/preference management.
 */
@Service
@Transactional
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final SendLogRepository sendLogRepository;
    private final NotificationTemplateRepository notificationTemplateRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final DndRuleRepository dndRuleRepository;
    private final AuditService auditService;

    public NotificationService(SendLogRepository sendLogRepository,
                               NotificationTemplateRepository notificationTemplateRepository,
                               SubscriptionRepository subscriptionRepository,
                               DndRuleRepository dndRuleRepository,
                               AuditService auditService) {
        this.sendLogRepository = sendLogRepository;
        this.notificationTemplateRepository = notificationTemplateRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.dndRuleRepository = dndRuleRepository;
        this.auditService = auditService;
    }

    /**
     * Creates a notification send-log entry with idempotency, template rendering,
     * subscription checks, and DND window awareness.
     *
     * @param userId       the target user
     * @param type         the notification type
     * @param referenceId  the reference entity ID (e.g. registration ID)
     * @param templateVars variables to substitute into the template
     */
    public void sendNotification(String userId,
                                 NotificationType type,
                                 String referenceId,
                                 Map<String, String> templateVars) {
        String idempotencyKey = type.name() + ":" + userId + ":" + referenceId;

        if (sendLogRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.debug("Duplicate notification skipped: type={}, referenceId={}", type, referenceId);
            return;
        }

        Optional<NotificationTemplate> templateOpt = notificationTemplateRepository.findByNotificationType(type);
        if (templateOpt.isEmpty()) {
            recordUndeliverableNotification(
                    userId,
                    type,
                    referenceId,
                    idempotencyKey,
                    "Notification template is missing");
            return;
        }
        NotificationTemplate template = templateOpt.get();
        if (!template.isActive()) {
            recordUndeliverableNotification(
                    userId,
                    type,
                    referenceId,
                    idempotencyKey,
                    "Notification template is inactive");
            return;
        }

        Optional<Subscription> subscriptionOpt = subscriptionRepository.findByUserIdAndNotificationType(userId, type);
        if (subscriptionOpt.isPresent() && !subscriptionOpt.get().isEnabled()) {
            log.debug("Notification suppressed by subscription preference: type={}, userId={}", type, userId);
            return;
        }

        String subject = renderTemplate(template.getSubjectTemplate(), templateVars);
        String body = renderTemplate(template.getBodyTemplate(), templateVars);

        SendLog sendLog = new SendLog();
        sendLog.setUserId(userId);
        sendLog.setNotificationType(type);
        sendLog.setSubject(subject);
        sendLog.setBody(body);
        sendLog.setStatus(SendStatus.PENDING);
        sendLog.setIdempotencyKey(idempotencyKey);
        sendLog.setReferenceId(referenceId);

        if (isInDndWindow(userId)) {
            DndRule rule = dndRuleRepository.findById(userId).orElse(null);
            if (rule != null) {
                sendLog.setNextAttemptAt(computeNextAttemptAfterDnd(rule));
            }
            log.debug("Notification deferred due to DND window: type={}, userId={}", type, userId);
        }

        sendLogRepository.save(sendLog);
        log.info("Notification created: type={}, referenceId={}, status=PENDING", type, referenceId);
    }

    /**
     * Checks whether the given user is currently inside their do-not-disturb window.
     *
     * @param userId the user to check
     * @return {@code true} if the user is in a DND window
     */
    public boolean isInDndWindow(String userId) {
        Optional<DndRule> ruleOpt = dndRuleRepository.findById(userId);
        if (ruleOpt.isEmpty()) {
            return false;
        }

        DndRule rule = ruleOpt.get();
        if (!rule.isEnabled()) {
            return false;
        }

        LocalTime now = LocalTime.now();
        LocalTime start = rule.getStartTime();
        LocalTime end = rule.getEndTime();

        if (start.isAfter(end)) {
            // Overnight window (e.g. 21:00 - 07:00)
            return now.isAfter(start) || now.isBefore(end);
        } else {
            // Daytime window (e.g. 13:00 - 15:00)
            return now.isAfter(start) && now.isBefore(end);
        }
    }

    /**
     * Attempts delivery of a pending or retrying notification. For in-app delivery
     * the notification is immediately marked as delivered. On failure, exponential
     * backoff is applied up to {@code maxAttempts}.
     *
     * @param sendLog the send log entry to deliver
     */
    public void attemptDelivery(SendLog sendLog) {
        sendLog.setAttemptCount(sendLog.getAttemptCount() + 1);
        sendLog.setLastAttemptAt(Instant.now());

        try {
            // In-app delivery: mark as delivered immediately
            sendLog.setStatus(SendStatus.DELIVERED);
            sendLog.setDeliveredAt(Instant.now());
            sendLog.setNextAttemptAt(null);
            sendLogRepository.save(sendLog);

            auditService.log(
                    AuditActionType.NOTIFICATION_SENT,
                    "SYSTEM",
                    "NotificationRetryScheduler",
                    "SYSTEM",
                    "SendLog",
                    sendLog.getId(),
                    "Notification delivered: type=" + sendLog.getNotificationType()
            );

            log.info("Notification delivered: id={}, type={}, attempt={}",
                    sendLog.getId(), sendLog.getNotificationType(), sendLog.getAttemptCount());

        } catch (Exception e) {
            log.error("Notification delivery failed: id={}, attempt={}", sendLog.getId(), sendLog.getAttemptCount(), e);

            if (sendLog.getAttemptCount() < sendLog.getMaxAttempts()) {
                sendLog.setStatus(SendStatus.FAILED_RETRYING);
                long backoffSeconds = computeBackoff(sendLog.getAttemptCount());
                sendLog.setNextAttemptAt(Instant.now().plusSeconds(backoffSeconds));
                sendLog.setLastError(e.getMessage());
            } else {
                sendLog.setStatus(SendStatus.PERMANENTLY_FAILED);
                sendLog.setLastError(e.getMessage());
                sendLog.setNextAttemptAt(null);

                auditService.log(
                        AuditActionType.NOTIFICATION_FAILED,
                        "SYSTEM",
                        "NotificationRetryScheduler",
                        "SYSTEM",
                        "SendLog",
                        sendLog.getId(),
                        "Notification permanently failed after " + sendLog.getAttemptCount() + " attempts"
                );
            }
            sendLogRepository.save(sendLog);
        }
    }

    /**
     * Computes the backoff duration in seconds for a given attempt count.
     * Yields: 12, 24, 48, 96, 192 (caps at 600).
     *
     * @param attemptCount the current attempt number (1-based)
     * @return backoff duration in seconds, capped at {@link AppConstants#NOTIFICATION_BACKOFF_MAX_SECONDS}
     */
    long computeBackoff(int attemptCount) {
        long backoff = AppConstants.NOTIFICATION_INITIAL_BACKOFF_SECONDS * (long) Math.pow(2, attemptCount - 1);
        return Math.min(backoff, AppConstants.NOTIFICATION_BACKOFF_MAX_SECONDS);
    }

    /**
     * Finds all notifications eligible for retry: PENDING or FAILED_RETRYING
     * with a next-attempt time in the past.
     *
     * @return list of retryable send log entries
     */
    @Transactional(readOnly = true)
    public List<SendLog> findRetryableNotifications() {
        return sendLogRepository.findRetryableNotifications(Instant.now());
    }

    /**
     * Returns a paginated list of delivered notifications for a user.
     *
     * @param userId   the user ID
     * @param pageable pagination parameters
     * @return page of notification responses
     */
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(String userId, Pageable pageable) {
        Page<SendLog> page = sendLogRepository.findByUserIdAndStatus(userId, SendStatus.DELIVERED, pageable);
        return page.map(this::mapToResponse);
    }

    /**
     * Returns the count of unread delivered notifications for a user.
     *
     * @param userId the user ID
     * @return unread notification count
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(String userId) {
        return sendLogRepository.countByUserIdAndStatusAndReadAtIsNull(userId, SendStatus.DELIVERED);
    }

    /**
     * Marks a notification as read for the given user.
     *
     * @param notificationId the send log ID
     * @param userId         the user ID (ownership check)
     * @throws BusinessException if the notification is not found or does not belong to the user
     */
    public void markAsRead(String notificationId, String userId) {
        SendLog sendLog = sendLogRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException("Notification not found", 404, "NOT_FOUND"));

        if (!sendLog.getUserId().equals(userId)) {
            throw new BusinessException("Notification does not belong to user", 403, "FORBIDDEN");
        }

        sendLog.setReadAt(Instant.now());
        sendLogRepository.save(sendLog);
        log.debug("Notification marked as read: id={}", notificationId);
    }

    /**
     * Returns all notification subscriptions for a user.
     *
     * @param userId the user ID
     * @return list of subscriptions
     */
    @Transactional(readOnly = true)
    public List<Subscription> getSubscriptions(String userId) {
        return subscriptionRepository.findByUserId(userId);
    }

    /**
     * Creates or updates notification subscriptions for a user.
     *
     * @param userId        the user ID
     * @param subscriptions list of subscription updates, each containing "notificationType" and "enabled"
     */
    public void updateSubscriptions(String userId, List<Map<String, Object>> subscriptions) {
        for (Map<String, Object> sub : subscriptions) {
            Object typeValue = sub.get("notificationType");
            if (typeValue == null) {
                typeValue = sub.get("type");
            }

            if (!(typeValue instanceof String typeName) || typeName.isBlank()) {
                throw new BusinessException("notificationType is required", 422, "VALIDATION_ERROR");
            }

            NotificationType type = NotificationType.valueOf(typeName);
            Object enabledValue = sub.get("enabled");
            boolean enabled = Boolean.TRUE.equals(enabledValue);

            Subscription subscription = subscriptionRepository.findByUserIdAndNotificationType(userId, type)
                    .orElseGet(() -> {
                        Subscription newSub = new Subscription();
                        newSub.setUserId(userId);
                        newSub.setNotificationType(type);
                        return newSub;
                    });

            subscription.setEnabled(enabled);
            subscriptionRepository.save(subscription);
        }

        log.info("Subscriptions updated for userId={}, count={}", userId, subscriptions.size());
    }

    /**
     * Returns the DND settings for a user, creating a default if none exists.
     *
     * @param userId the user ID
     * @return the DND rule
     */
    @Transactional(readOnly = true)
    public DndRule getDndSettings(String userId) {
        return dndRuleRepository.findById(userId)
                .orElseGet(() -> {
                    DndRule rule = new DndRule();
                    rule.setUserId(userId);
                    return rule;
                });
    }

    /**
     * Creates or updates the DND settings for a user.
     *
     * @param userId    the user ID
     * @param startTime DND start time in HH:mm format
     * @param endTime   DND end time in HH:mm format
     * @param enabled   whether DND is enabled
     * @return the persisted DND rule
     */
    public DndRule updateDndSettings(String userId, String startTime, String endTime, boolean enabled) {
        DndRule rule = dndRuleRepository.findById(userId)
                .orElseGet(() -> {
                    DndRule newRule = new DndRule();
                    newRule.setUserId(userId);
                    return newRule;
                });

        rule.setStartTime(LocalTime.parse(startTime));
        rule.setEndTime(LocalTime.parse(endTime));
        rule.setEnabled(enabled);

        DndRule saved = dndRuleRepository.save(rule);
        log.info("DND settings updated for userId={}", userId);
        return saved;
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Renders a template string by replacing {{key}} placeholders with values
     * from the provided map.
     */
    private String renderTemplate(String template, Map<String, String> vars) {
        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    /**
     * Computes the next attempt time after the DND window ends.
     */
    private Instant computeNextAttemptAfterDnd(DndRule rule) {
        LocalTime end = rule.getEndTime();
        LocalTime now = LocalTime.now();

        long minutesUntilEnd;
        if (now.isAfter(end) || now.equals(end)) {
            // DND end is tomorrow
            minutesUntilEnd = ChronoUnit.MINUTES.between(now, LocalTime.MAX)
                    + ChronoUnit.MINUTES.between(LocalTime.MIN, end) + 1;
        } else {
            minutesUntilEnd = ChronoUnit.MINUTES.between(now, end);
        }

        return Instant.now().plus(minutesUntilEnd, ChronoUnit.MINUTES);
    }

    /**
     * Maps a {@link SendLog} entity to a {@link NotificationResponse} DTO.
     */
    private NotificationResponse mapToResponse(SendLog sendLog) {
        NotificationResponse response = new NotificationResponse();
        response.setId(sendLog.getId());
        response.setNotificationType(sendLog.getNotificationType().name());
        response.setSubject(sendLog.getSubject());
        response.setBody(sendLog.getBody());
        response.setStatus(sendLog.getStatus().name());
        response.setRead(sendLog.getReadAt() != null);
        response.setDeliveredAt(sendLog.getDeliveredAt());
        response.setCreatedAt(sendLog.getCreatedAt());
        return response;
    }

    private void recordUndeliverableNotification(String userId,
                                                 NotificationType type,
                                                 String referenceId,
                                                 String idempotencyKey,
                                                 String reason) {
        String severity = RequiredNotificationTemplates.isRequired(type) ? "required" : "optional";
        String errorMessage = reason + " for " + severity + " notification type " + type.name();

        SendLog sendLog = new SendLog();
        sendLog.setUserId(userId);
        sendLog.setNotificationType(type);
        sendLog.setReferenceId(referenceId);
        sendLog.setIdempotencyKey(idempotencyKey);
        sendLog.setSubject("Notification delivery unavailable");
        sendLog.setBody(errorMessage);
        sendLog.setStatus(SendStatus.PERMANENTLY_FAILED);
        sendLog.setLastError(errorMessage);
        sendLog.setNextAttemptAt(null);
        sendLogRepository.save(sendLog);

        auditService.log(
                AuditActionType.NOTIFICATION_FAILED,
                "SYSTEM",
                "NotificationService",
                "SYSTEM",
                "SendLog",
                sendLog.getId(),
                errorMessage
        );

        log.error("Notification not queued: type={}, userId={}, referenceId={}, reason={}",
                type, userId, referenceId, errorMessage);
    }
}
