package com.eventops.repository.notification;

import com.eventops.domain.notification.Subscription;
import com.eventops.domain.notification.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, String> {
    List<Subscription> findByUserId(String userId);
    Optional<Subscription> findByUserIdAndNotificationType(String userId, NotificationType type);
}
