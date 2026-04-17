package com.eventops.repository;

import com.eventops.domain.checkin.CheckInRecord;
import com.eventops.domain.checkin.CheckInStatus;
import com.eventops.repository.checkin.CheckInRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class CheckInRecordRepositoryTest {

    @Autowired
    private CheckInRecordRepository checkInRecordRepository;

    @BeforeEach
    void clean() {
        checkInRecordRepository.deleteAll();
    }

    private CheckInRecord build(String sessionId, String userId, CheckInStatus status, Instant checkedInAt) {
        CheckInRecord r = new CheckInRecord();
        r.setSessionId(sessionId);
        r.setUserId(userId);
        r.setStaffId("staff1");
        r.setStatus(status);
        r.setCheckedInAt(checkedInAt);
        // deviceTokenEncrypted intentionally null — converter requires Spring context
        return r;
    }

    @Test
    void saveAndFindById_roundTrip() {
        CheckInRecord saved = checkInRecordRepository.save(
                build("s1", "u1", CheckInStatus.CHECKED_IN, Instant.now()));
        Optional<CheckInRecord> found = checkInRecordRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("s1", found.get().getSessionId());
    }

    @Test
    void findBySessionId_returnsAllForSession() {
        checkInRecordRepository.save(build("s1", "u1", CheckInStatus.CHECKED_IN, Instant.now()));
        checkInRecordRepository.save(build("s1", "u2", CheckInStatus.CHECKED_IN, Instant.now()));
        checkInRecordRepository.save(build("s2", "u1", CheckInStatus.CHECKED_IN, Instant.now()));

        List<CheckInRecord> s1 = checkInRecordRepository.findBySessionId("s1");
        assertEquals(2, s1.size());
    }

    @Test
    void findBySessionIdAndUserId_filtersCorrectly() {
        checkInRecordRepository.save(build("s1", "u1", CheckInStatus.CHECKED_IN, Instant.now()));
        checkInRecordRepository.save(build("s1", "u1", CheckInStatus.DENIED_DUPLICATE, Instant.now()));
        checkInRecordRepository.save(build("s1", "u2", CheckInStatus.CHECKED_IN, Instant.now()));

        List<CheckInRecord> matched = checkInRecordRepository.findBySessionIdAndUserId("s1", "u1");
        assertEquals(2, matched.size());

        List<CheckInRecord> none = checkInRecordRepository.findBySessionIdAndUserId("s1", "ghost");
        assertEquals(0, none.size());
    }

    @Test
    void existsBySessionIdAndUserIdAndStatus_trueWhenMatching() {
        checkInRecordRepository.save(build("s1", "u1", CheckInStatus.CHECKED_IN, Instant.now()));
        assertTrue(checkInRecordRepository
                .existsBySessionIdAndUserIdAndStatus("s1", "u1", CheckInStatus.CHECKED_IN));
        assertFalse(checkInRecordRepository
                .existsBySessionIdAndUserIdAndStatus("s1", "u1", CheckInStatus.DENIED_DUPLICATE));
        assertFalse(checkInRecordRepository
                .existsBySessionIdAndUserIdAndStatus("s2", "u1", CheckInStatus.CHECKED_IN));
    }

    @Test
    void findBySessionIdAndStatus_filtersCorrectly() {
        checkInRecordRepository.save(build("s1", "u1", CheckInStatus.CHECKED_IN, Instant.now()));
        checkInRecordRepository.save(build("s1", "u2", CheckInStatus.CHECKED_IN, Instant.now()));
        checkInRecordRepository.save(build("s1", "u3", CheckInStatus.DENIED_INVALID_PASSCODE, Instant.now()));

        List<CheckInRecord> checkedIn = checkInRecordRepository
                .findBySessionIdAndStatus("s1", CheckInStatus.CHECKED_IN);
        assertEquals(2, checkedIn.size());

        List<CheckInRecord> denied = checkInRecordRepository
                .findBySessionIdAndStatus("s1", CheckInStatus.DENIED_INVALID_PASSCODE);
        assertEquals(1, denied.size());
    }

    @Test
    void findTopByUserIdAndStatusAndSessionIdNotOrderByCheckedInAtDesc_returnsMostRecent() {
        Instant t1 = Instant.now().minusSeconds(300);
        Instant t2 = Instant.now().minusSeconds(120);
        Instant t3 = Instant.now().minusSeconds(30);

        checkInRecordRepository.save(build("s1", "u1", CheckInStatus.CHECKED_IN, t1));
        checkInRecordRepository.save(build("s2", "u1", CheckInStatus.CHECKED_IN, t2));
        checkInRecordRepository.save(build("s3", "u1", CheckInStatus.CHECKED_IN, t3));

        Optional<CheckInRecord> recent = checkInRecordRepository
                .findTopByUserIdAndStatusAndSessionIdNotOrderByCheckedInAtDesc(
                        "u1", CheckInStatus.CHECKED_IN, "s3");
        assertTrue(recent.isPresent());
        assertEquals("s2", recent.get().getSessionId());
    }
}
