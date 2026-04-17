package com.eventops.repository;

import com.eventops.domain.registration.Registration;
import com.eventops.domain.registration.RegistrationStatus;
import com.eventops.repository.registration.RegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class RegistrationRepositoryTest {

    @Autowired
    private RegistrationRepository registrationRepository;

    @BeforeEach
    void clean() {
        registrationRepository.deleteAll();
    }

    private Registration buildRegistration(String userId, String sessionId, RegistrationStatus status, Integer waitlistPos) {
        Registration r = new Registration();
        r.setUserId(userId);
        r.setSessionId(sessionId);
        r.setStatus(status);
        r.setWaitlistPosition(waitlistPos);
        return r;
    }

    @Test
    void saveAndFindById_roundTrip() {
        Registration saved = registrationRepository.save(
                buildRegistration("u1", "s1", RegistrationStatus.CONFIRMED, null));
        Optional<Registration> found = registrationRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("u1", found.get().getUserId());
    }

    @Test
    void existsByUserIdAndSessionId_trueWhenExists() {
        registrationRepository.save(buildRegistration("u1", "s1", RegistrationStatus.CONFIRMED, null));
        assertTrue(registrationRepository.existsByUserIdAndSessionId("u1", "s1"));
    }

    @Test
    void existsByUserIdAndSessionId_falseWhenNotExists() {
        assertFalse(registrationRepository.existsByUserIdAndSessionId("u1", "s1"));
    }

    @Test
    void findByUserIdAndSessionId_found() {
        registrationRepository.save(buildRegistration("u1", "s1", RegistrationStatus.CONFIRMED, null));
        Optional<Registration> found = registrationRepository.findByUserIdAndSessionId("u1", "s1");
        assertTrue(found.isPresent());
    }

    @Test
    void findByUserId_returnsAllForUser() {
        registrationRepository.save(buildRegistration("u1", "s1", RegistrationStatus.CONFIRMED, null));
        registrationRepository.save(buildRegistration("u1", "s2", RegistrationStatus.WAITLISTED, 1));
        registrationRepository.save(buildRegistration("u2", "s1", RegistrationStatus.CONFIRMED, null));
        List<Registration> regs = registrationRepository.findByUserId("u1");
        assertEquals(2, regs.size());
    }

    @Test
    void findBySessionIdAndStatus_filtersCorrectly() {
        registrationRepository.save(buildRegistration("u1", "s1", RegistrationStatus.CONFIRMED, null));
        registrationRepository.save(buildRegistration("u2", "s1", RegistrationStatus.WAITLISTED, 1));
        registrationRepository.save(buildRegistration("u3", "s1", RegistrationStatus.CONFIRMED, null));

        List<Registration> confirmed = registrationRepository.findBySessionIdAndStatus("s1", RegistrationStatus.CONFIRMED);
        assertEquals(2, confirmed.size());

        List<Registration> waitlisted = registrationRepository.findBySessionIdAndStatus("s1", RegistrationStatus.WAITLISTED);
        assertEquals(1, waitlisted.size());
    }

    @Test
    void findWaitlistedBySessionOrderByPosition_orderedAscending() {
        registrationRepository.save(buildRegistration("u1", "s1", RegistrationStatus.WAITLISTED, 3));
        registrationRepository.save(buildRegistration("u2", "s1", RegistrationStatus.WAITLISTED, 1));
        registrationRepository.save(buildRegistration("u3", "s1", RegistrationStatus.WAITLISTED, 2));
        registrationRepository.save(buildRegistration("u4", "s1", RegistrationStatus.CONFIRMED, null));

        List<Registration> waitlisted = registrationRepository.findWaitlistedBySessionOrderByPosition("s1");
        assertEquals(3, waitlisted.size());
        assertEquals(1, waitlisted.get(0).getWaitlistPosition());
        assertEquals(2, waitlisted.get(1).getWaitlistPosition());
        assertEquals(3, waitlisted.get(2).getWaitlistPosition());
    }

    @Test
    void countActiveRegistrations_countsConfirmedAndPromoted() {
        registrationRepository.save(buildRegistration("u1", "s1", RegistrationStatus.CONFIRMED, null));
        registrationRepository.save(buildRegistration("u2", "s1", RegistrationStatus.PROMOTED, null));
        registrationRepository.save(buildRegistration("u3", "s1", RegistrationStatus.WAITLISTED, 1));
        registrationRepository.save(buildRegistration("u4", "s1", RegistrationStatus.CANCELLED, null));

        long count = registrationRepository.countActiveRegistrations("s1");
        assertEquals(2, count);
    }
}
