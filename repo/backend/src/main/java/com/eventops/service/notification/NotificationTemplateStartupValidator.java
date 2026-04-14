package com.eventops.service.notification;

import com.eventops.domain.notification.NotificationTemplate;
import com.eventops.domain.notification.NotificationType;
import com.eventops.repository.notification.NotificationTemplateRepository;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Fails startup when required notification templates are missing or inactive.
 */
@Component
@Profile("!test")
public class NotificationTemplateStartupValidator implements SmartInitializingSingleton {

    private final NotificationTemplateRepository notificationTemplateRepository;

    public NotificationTemplateStartupValidator(NotificationTemplateRepository notificationTemplateRepository) {
        this.notificationTemplateRepository = notificationTemplateRepository;
    }

    @Override
    public void afterSingletonsInstantiated() {
        validateRequiredTemplates();
    }

    void validateRequiredTemplates() {
        Set<NotificationType> available = notificationTemplateRepository.findAll().stream()
                .filter(NotificationTemplate::isActive)
                .map(NotificationTemplate::getNotificationType)
                .collect(() -> EnumSet.noneOf(NotificationType.class), Set::add, Set::addAll);

        Set<NotificationType> missing = EnumSet.copyOf(RequiredNotificationTemplates.all());
        missing.removeAll(available);
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Required notification templates are missing or inactive: " + missing);
        }
    }
}
