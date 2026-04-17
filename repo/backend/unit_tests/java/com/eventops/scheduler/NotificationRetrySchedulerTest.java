package com.eventops.scheduler;

import com.eventops.domain.notification.SendLog;
import com.eventops.service.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationRetrySchedulerTest {

    @Mock NotificationService notificationService;
    @InjectMocks NotificationRetryScheduler scheduler;

    private SendLog log(String id) {
        SendLog l = new SendLog();
        l.setId(id);
        return l;
    }

    @Test
    void retryPendingNotifications_earlyReturnWhenNoRetryable() {
        when(notificationService.findRetryableNotifications()).thenReturn(List.of());
        scheduler.retryPendingNotifications();
        verify(notificationService, never()).attemptDelivery(any());
    }

    @Test
    void retryPendingNotifications_deliversEachPending() {
        when(notificationService.findRetryableNotifications())
                .thenReturn(List.of(log("n1"), log("n2")));

        scheduler.retryPendingNotifications();

        verify(notificationService, times(2)).attemptDelivery(any());
    }

    @Test
    void retryPendingNotifications_continuesDespiteFailures() {
        SendLog l1 = log("n1");
        SendLog l2 = log("n2");
        when(notificationService.findRetryableNotifications()).thenReturn(List.of(l1, l2));
        doThrow(new RuntimeException("smtp down")).when(notificationService).attemptDelivery(l1);

        assertDoesNotThrow(scheduler::retryPendingNotifications);
        verify(notificationService).attemptDelivery(l1);
        verify(notificationService).attemptDelivery(l2);
    }

    @Test
    void retryPendingNotifications_allFailuresStillCompletes() {
        when(notificationService.findRetryableNotifications())
                .thenReturn(List.of(log("n1")));
        doThrow(new RuntimeException("fail")).when(notificationService).attemptDelivery(any());

        assertDoesNotThrow(scheduler::retryPendingNotifications);
    }
}
