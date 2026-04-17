package com.eventops.repository;

import com.eventops.domain.audit.WatermarkPolicy;
import com.eventops.repository.audit.WatermarkPolicyRepository;
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
class WatermarkPolicyRepositoryTest {

    @Autowired
    private WatermarkPolicyRepository watermarkPolicyRepository;

    @BeforeEach
    void clean() {
        watermarkPolicyRepository.deleteAll();
    }

    private WatermarkPolicy build(String reportType, String roleType, boolean active) {
        WatermarkPolicy p = new WatermarkPolicy();
        p.setReportType(reportType);
        p.setRoleType(roleType);
        p.setActive(active);
        p.setDownloadAllowed(true);
        p.setWatermarkTemplate("CONFIDENTIAL — {{user}}");
        return p;
    }

    @Test
    void saveAndFindById_roundTrip() {
        WatermarkPolicy saved = watermarkPolicyRepository.save(build("AUDIT_LOG", "AUDITOR", true));
        Optional<WatermarkPolicy> found = watermarkPolicyRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("AUDIT_LOG", found.get().getReportType());
        assertEquals("AUDITOR", found.get().getRoleType());
    }

    @Test
    void findByReportTypeAndRoleType_filtersCorrectly() {
        watermarkPolicyRepository.save(build("AUDIT_LOG", "AUDITOR", true));
        watermarkPolicyRepository.save(build("AUDIT_LOG", "SYSTEM_ADMIN", true));
        watermarkPolicyRepository.save(build("FINANCE_REPORT", "AUDITOR", true));

        List<WatermarkPolicy> matched = watermarkPolicyRepository
                .findByReportTypeAndRoleType("AUDIT_LOG", "AUDITOR");
        assertEquals(1, matched.size());

        List<WatermarkPolicy> none = watermarkPolicyRepository
                .findByReportTypeAndRoleType("ROSTER", "AUDITOR");
        assertEquals(0, none.size());
    }

    @Test
    void findByActiveTrue_returnsOnlyActive() {
        watermarkPolicyRepository.save(build("AUDIT_LOG", "AUDITOR", true));
        watermarkPolicyRepository.save(build("ROSTER", "EVENT_STAFF", false));
        watermarkPolicyRepository.save(build("FINANCE_REPORT", "FINANCE_MANAGER", true));

        List<WatermarkPolicy> active = watermarkPolicyRepository.findByActiveTrue();
        assertEquals(2, active.size());
    }
}
