package com.eventops.repository;

import com.eventops.domain.notification.NotificationType;
import com.eventops.domain.notification.Subscription;
import com.eventops.repository.notification.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class SubscriptionRepositoryTest {

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @BeforeEach
    void clean() {
        subscriptionRepository.deleteAll();
    }

    private Subscription build(String userId, NotificationType type, boolean enabled) {
        Subscription s = new Subscription();
        s.setUserId(userId);
        s.setNotificationType(type);
        s.setEnabled(enabled);
        return s;
    }

    @Test
    void saveAndFindById_roundTrip() {
        Subscription saved = subscriptionRepository.save(
                build("u1", NotificationType.REGISTRATION_CONFIRMATION, true));
        Optional<Subscription> found = subscriptionRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("u1", found.get().getUserId());
        assertTrue(found.get().isEnabled());
    }

    @Test
    void findByUserId_returnsAllForUser() {
        subscriptionRepository.save(build("u1", NotificationType.REGISTRATION_CONFIRMATION, true));
        subscriptionRepository.save(build("u1", NotificationType.WAITLIST_PROMOTION, false));
        subscriptionRepository.save(build("u2", NotificationType.REGISTRATION_CONFIRMATION, true));

        List<Subscription> u1 = subscriptionRepository.findByUserId("u1");
        assertEquals(2, u1.size());
    }

    @Test
    void findByUserIdAndNotificationType_found() {
        subscriptionRepository.save(build("u1", NotificationType.REGISTRATION_CONFIRMATION, true));
        Optional<Subscription> found = subscriptionRepository
                .findByUserIdAndNotificationType("u1", NotificationType.REGISTRATION_CONFIRMATION);
        assertTrue(found.isPresent());
        assertTrue(found.get().isEnabled());
    }

    @Test
    void findByUserIdAndNotificationType_notFound() {
        Optional<Subscription> found = subscriptionRepository
                .findByUserIdAndNotificationType("u1", NotificationType.SYSTEM_ALERT);
        assertTrue(found.isEmpty());
    }

    @Test
    void uniqueConstraint_duplicateUserAndType_throws() {
        subscriptionRepository.save(build("u1", NotificationType.REGISTRATION_CONFIRMATION, true));
        Subscription duplicate = build("u1", NotificationType.REGISTRATION_CONFIRMATION, false);
        assertThrows(DataIntegrityViolationException.class,
                () -> subscriptionRepository.saveAndFlush(duplicate));
    }
}
