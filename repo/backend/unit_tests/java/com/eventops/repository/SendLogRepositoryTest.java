package com.eventops.repository;

import com.eventops.domain.notification.NotificationType;
import com.eventops.domain.notification.SendLog;
import com.eventops.domain.notification.SendStatus;
import com.eventops.repository.notification.SendLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class SendLogRepositoryTest {

    @Autowired
    private SendLogRepository sendLogRepository;

    @BeforeEach
    void clean() {
        sendLogRepository.deleteAll();
    }

    private SendLog build(String userId, NotificationType type, SendStatus status,
                          String idempotencyKey, Instant nextAttemptAt, Instant readAt) {
        SendLog l = new SendLog();
        l.setUserId(userId);
        l.setNotificationType(type);
        l.setStatus(status);
        l.setSubject("subject");
        l.setBody("body");
        l.setIdempotencyKey(idempotencyKey);
        l.setNextAttemptAt(nextAttemptAt);
        l.setReadAt(readAt);
        return l;
    }

    @Test
    void saveAndFindById_roundTrip() {
        SendLog saved = sendLogRepository.save(
                build("u1", NotificationType.REGISTRATION_CONFIRMATION, SendStatus.PENDING,
                        "key-1", null, null));
        Optional<SendLog> found = sendLogRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals(SendStatus.PENDING, found.get().getStatus());
    }

    @Test
    void findByIdempotencyKey_found() {
        sendLogRepository.save(build("u1", NotificationType.REGISTRATION_CONFIRMATION,
                SendStatus.DELIVERED, "key-unique", null, null));
        Optional<SendLog> found = sendLogRepository.findByIdempotencyKey("key-unique");
        assertTrue(found.isPresent());
    }

    @Test
    void existsByIdempotencyKey_trueWhenExists() {
        sendLogRepository.save(build("u1", NotificationType.REGISTRATION_CONFIRMATION,
                SendStatus.DELIVERED, "key-exists", null, null));
        assertTrue(sendLogRepository.existsByIdempotencyKey("key-exists"));
        assertFalse(sendLogRepository.existsByIdempotencyKey("missing"));
    }

    @Test
    void findByUserId_returnsAllForUser() {
        sendLogRepository.save(build("u1", NotificationType.REGISTRATION_CONFIRMATION,
                SendStatus.DELIVERED, "k1", null, null));
        sendLogRepository.save(build("u1", NotificationType.WAITLIST_PROMOTION,
                SendStatus.PENDING, "k2", null, null));
        sendLogRepository.save(build("u2", NotificationType.REGISTRATION_CONFIRMATION,
                SendStatus.DELIVERED, "k3", null, null));

        assertEquals(2, sendLogRepository.findByUserId("u1").size());
    }

    @Test
    void findByUserIdAndStatus_paged() {
        for (int i = 0; i < 5; i++) {
            sendLogRepository.save(build("u1", NotificationType.REGISTRATION_CONFIRMATION,
                    SendStatus.DELIVERED, "k-deliv-" + i, null, null));
        }
        sendLogRepository.save(build("u1", NotificationType.WAITLIST_PROMOTION,
                SendStatus.PENDING, "k-pend-1", null, null));

        Page<SendLog> page = sendLogRepository.findByUserIdAndStatus(
                "u1", SendStatus.DELIVERED, PageRequest.of(0, 3));
        assertEquals(5, page.getTotalElements());
        assertEquals(3, page.getContent().size());
    }

    @Test
    void countByUserIdAndStatusAndReadAtIsNull_countsUnread() {
        sendLogRepository.save(build("u1", NotificationType.REGISTRATION_CONFIRMATION,
                SendStatus.DELIVERED, "k1", null, null));
        sendLogRepository.save(build("u1", NotificationType.WAITLIST_PROMOTION,
                SendStatus.DELIVERED, "k2", null, null));
        sendLogRepository.save(build("u1", NotificationType.SYSTEM_ALERT,
                SendStatus.DELIVERED, "k3", null, Instant.now()));

        long unread = sendLogRepository.countByUserIdAndStatusAndReadAtIsNull("u1", SendStatus.DELIVERED);
        assertEquals(2, unread);
    }

    @Test
    void findRetryableNotifications_includesPendingAndPastDueRetrying() {
        Instant now = Instant.now();
        sendLogRepository.save(build("u1", NotificationType.REGISTRATION_CONFIRMATION,
                SendStatus.PENDING, "k-pending", now.minus(1, ChronoUnit.MINUTES), null));
        sendLogRepository.save(build("u2", NotificationType.WAITLIST_PROMOTION,
                SendStatus.FAILED_RETRYING, "k-due", now.minus(5, ChronoUnit.MINUTES), null));
        sendLogRepository.save(build("u3", NotificationType.SYSTEM_ALERT,
                SendStatus.FAILED_RETRYING, "k-future", now.plus(10, ChronoUnit.MINUTES), null));
        sendLogRepository.save(build("u4", NotificationType.IMPORT_COMPLETED,
                SendStatus.DELIVERED, "k-delivered", null, null));

        List<SendLog> retryable = sendLogRepository.findRetryableNotifications(now);
        assertEquals(2, retryable.size());
    }

    @Test
    void countByStatus_returnsCount() {
        sendLogRepository.save(build("u1", NotificationType.REGISTRATION_CONFIRMATION,
                SendStatus.DELIVERED, "k1", null, null));
        sendLogRepository.save(build("u2", NotificationType.WAITLIST_PROMOTION,
                SendStatus.DELIVERED, "k2", null, null));
        sendLogRepository.save(build("u3", NotificationType.SYSTEM_ALERT,
                SendStatus.PENDING, "k3", null, null));

        assertEquals(2, sendLogRepository.countByStatus(SendStatus.DELIVERED));
        assertEquals(1, sendLogRepository.countByStatus(SendStatus.PENDING));
        assertEquals(0, sendLogRepository.countByStatus(SendStatus.PERMANENTLY_FAILED));
    }
}
