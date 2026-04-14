package com.eventops.repository.notification;

import com.eventops.domain.notification.NotificationTemplate;
import com.eventops.domain.notification.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, String> {
    Optional<NotificationTemplate> findByNotificationType(NotificationType type);
}
