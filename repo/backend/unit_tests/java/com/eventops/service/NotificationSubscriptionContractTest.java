package com.eventops.service;

import com.eventops.audit.logging.AuditService;
import com.eventops.common.exception.BusinessException;
import com.eventops.domain.notification.NotificationType;
import com.eventops.domain.notification.Subscription;
import com.eventops.repository.notification.DndRuleRepository;
import com.eventops.repository.notification.NotificationTemplateRepository;
import com.eventops.repository.notification.SendLogRepository;
import com.eventops.repository.notification.SubscriptionRepository;
import com.eventops.service.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationSubscriptionContractTest {

    @Mock
    private SendLogRepository sendLogRepository;

    @Mock
    private NotificationTemplateRepository notificationTemplateRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private DndRuleRepository dndRuleRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private NotificationService notificationService;

    private static final String USER_ID = "user-1";

    @Test
    void updateSubscriptions_acceptsNotificationTypeKey() {
        Subscription existing = new Subscription();
        existing.setUserId(USER_ID);
        existing.setNotificationType(NotificationType.IMPORT_FAILED);
        existing.setEnabled(false);

        when(subscriptionRepository.findByUserIdAndNotificationType(USER_ID, NotificationType.IMPORT_FAILED))
                .thenReturn(Optional.of(existing));

        notificationService.updateSubscriptions(USER_ID, List.of(
                Map.of("notificationType", "IMPORT_FAILED", "enabled", true)
        ));

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertEquals(NotificationType.IMPORT_FAILED, captor.getValue().getNotificationType());
        assertTrue(captor.getValue().isEnabled());
    }

    @Test
    void updateSubscriptions_acceptsLegacyTypeKey() {
        when(subscriptionRepository.findByUserIdAndNotificationType(USER_ID, NotificationType.SYSTEM_ALERT))
                .thenReturn(Optional.empty());

        notificationService.updateSubscriptions(USER_ID, List.of(
                Map.of("type", "SYSTEM_ALERT", "enabled", true)
        ));

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertEquals(NotificationType.SYSTEM_ALERT, captor.getValue().getNotificationType());
        assertEquals(USER_ID, captor.getValue().getUserId());
        assertTrue(captor.getValue().isEnabled());
    }

    @Test
    void updateSubscriptions_throwsValidationError_whenTypeMissing() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> notificationService.updateSubscriptions(USER_ID, List.of(
                        Map.of("enabled", true)
                )));

        assertEquals("VALIDATION_ERROR", ex.getErrorCode());
    }
}
