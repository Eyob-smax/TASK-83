package com.eventops.service.notification;

import com.eventops.domain.notification.NotificationTemplate;
import com.eventops.domain.notification.NotificationType;
import com.eventops.repository.notification.NotificationTemplateRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationTemplateStartupValidatorTest {

    private final NotificationTemplateRepository repository = mock(NotificationTemplateRepository.class);
    private final NotificationTemplateStartupValidator validator =
            new NotificationTemplateStartupValidator(repository);

    @Test
    void validateRequiredTemplates_passesWhenAllRequiredTemplatesAreActive() {
        when(repository.findAll()).thenReturn(
                RequiredNotificationTemplates.all().stream()
                        .map(this::activeTemplate)
                        .toList()
        );

        assertDoesNotThrow(validator::validateRequiredTemplates);
    }

    @Test
    void validateRequiredTemplates_failsWhenRequiredTemplateMissing() {
        when(repository.findAll()).thenReturn(List.of(
                activeTemplate(NotificationType.CHECKIN_EXCEPTION),
                activeTemplate(NotificationType.CHECKIN_DEVICE_WARNING)
        ));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                validator::validateRequiredTemplates
        );

        org.junit.jupiter.api.Assertions.assertTrue(
                exception.getMessage().contains(NotificationType.REGISTRATION_CONFIRMATION.name()));
    }

    private NotificationTemplate activeTemplate(NotificationType type) {
        NotificationTemplate template = new NotificationTemplate();
        template.setNotificationType(type);
        template.setSubjectTemplate(type.name() + " subject");
        template.setBodyTemplate(type.name() + " body");
        template.setActive(true);
        return template;
    }
}
