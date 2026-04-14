package com.eventops.scheduler;

import com.eventops.domain.notification.SendLog;
import com.eventops.service.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Polls the notification retry queue and dispatches pending notifications
 * with exponential backoff. Max 5 attempts over ~10 minutes.
 */
@Component
public class NotificationRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetryScheduler.class);

    private final NotificationService notificationService;

    public NotificationRetryScheduler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Scheduled(fixedRate = 30000)
    public void retryPendingNotifications() {
        List<SendLog> retryable = notificationService.findRetryableNotifications();
        if (retryable.isEmpty()) {
            return;
        }

        log.info("Processing {} retryable notifications", retryable.size());
        int delivered = 0;
        int failed = 0;

        for (SendLog sendLog : retryable) {
            try {
                notificationService.attemptDelivery(sendLog);
                delivered++;
            } catch (Exception e) {
                failed++;
                log.error("Failed to deliver notification {}: {}", sendLog.getId(), e.getMessage());
            }
        }

        log.info("Notification retry complete: {} delivered, {} failed", delivered, failed);
    }
}
