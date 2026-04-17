package com.eventops.repository;

import com.eventops.domain.audit.AuditActionType;
import com.eventops.domain.audit.AuditEvent;
import com.eventops.repository.audit.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class AuditEventRepositoryTest {

    @Autowired
    private AuditEventRepository auditEventRepository;

    @BeforeEach
    void clean() {
        auditEventRepository.deleteAll();
    }

    private AuditEvent build(AuditActionType actionType, String operatorId, String entityType, String entityId) {
        AuditEvent e = new AuditEvent();
        e.setActionType(actionType);
        e.setOperatorId(operatorId);
        e.setOperatorName("Operator " + operatorId);
        e.setEntityType(entityType);
        e.setEntityId(entityId);
        e.setDescription("test event");
        return e;
    }

    @Test
    void saveAndFindById_roundTrip() {
        AuditEvent saved = auditEventRepository.save(build(AuditActionType.LOGIN_SUCCESS, "op1", "User", "u1"));
        Optional<AuditEvent> found = auditEventRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals(AuditActionType.LOGIN_SUCCESS, found.get().getActionType());
    }

    @Test
    void findByActionType_returnsMatching() {
        auditEventRepository.save(build(AuditActionType.LOGIN_SUCCESS, "op1", "User", "u1"));
        auditEventRepository.save(build(AuditActionType.LOGIN_FAILURE, "op2", "User", "u2"));
        auditEventRepository.save(build(AuditActionType.LOGIN_SUCCESS, "op3", "User", "u3"));

        List<AuditEvent> successes = auditEventRepository.findByActionType(AuditActionType.LOGIN_SUCCESS);
        assertEquals(2, successes.size());

        List<AuditEvent> failures = auditEventRepository.findByActionType(AuditActionType.LOGIN_FAILURE);
        assertEquals(1, failures.size());
    }

    @Test
    void findByOperatorId_returnsAllForOperator() {
        auditEventRepository.save(build(AuditActionType.LOGIN_SUCCESS, "op1", "User", "u1"));
        auditEventRepository.save(build(AuditActionType.LOGOUT, "op1", "User", "u1"));
        auditEventRepository.save(build(AuditActionType.LOGIN_SUCCESS, "op2", "User", "u2"));

        List<AuditEvent> op1Events = auditEventRepository.findByOperatorId("op1");
        assertEquals(2, op1Events.size());
    }

    @Test
    void findByEntityTypeAndEntityId_filtersCorrectly() {
        auditEventRepository.save(build(AuditActionType.REGISTRATION_CREATED, "op1", "Registration", "r1"));
        auditEventRepository.save(build(AuditActionType.REGISTRATION_CANCELLED, "op2", "Registration", "r1"));
        auditEventRepository.save(build(AuditActionType.REGISTRATION_CREATED, "op3", "Registration", "r2"));
        auditEventRepository.save(build(AuditActionType.LOGIN_SUCCESS, "op4", "User", "r1"));

        List<AuditEvent> r1Events = auditEventRepository.findByEntityTypeAndEntityId("Registration", "r1");
        assertEquals(2, r1Events.size());
    }

    @Test
    void findByCreatedAtBetween_filtersByDateRange() {
        auditEventRepository.save(build(AuditActionType.LOGIN_SUCCESS, "op1", "User", "u1"));
        auditEventRepository.save(build(AuditActionType.LOGIN_SUCCESS, "op2", "User", "u2"));

        Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant to = Instant.now().plus(1, ChronoUnit.HOURS);
        List<AuditEvent> within = auditEventRepository.findByCreatedAtBetween(from, to);
        assertEquals(2, within.size());

        Instant farPast = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant pastEnd = Instant.now().minus(5, ChronoUnit.DAYS);
        List<AuditEvent> none = auditEventRepository.findByCreatedAtBetween(farPast, pastEnd);
        assertEquals(0, none.size());
    }
}
