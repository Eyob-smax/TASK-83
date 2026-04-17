package com.eventops.repository;

import com.eventops.domain.finance.CostCenter;
import com.eventops.repository.finance.CostCenterRepository;
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
class CostCenterRepositoryTest {

    @Autowired
    private CostCenterRepository costCenterRepository;

    @BeforeEach
    void clean() {
        costCenterRepository.deleteAll();
    }

    private CostCenter build(String code, String name, String type, boolean active) {
        CostCenter c = new CostCenter();
        c.setCode(code);
        c.setName(name);
        c.setCenterType(type);
        c.setActive(active);
        return c;
    }

    @Test
    void saveAndFindById_roundTrip() {
        CostCenter saved = costCenterRepository.save(build("VEH-A", "Vehicle Type A", "VEHICLE_TYPE", true));
        Optional<CostCenter> found = costCenterRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("VEH-A", found.get().getCode());
    }

    @Test
    void findByCode_found() {
        costCenterRepository.save(build("VEH-A", "Vehicle Type A", "VEHICLE_TYPE", true));
        Optional<CostCenter> found = costCenterRepository.findByCode("VEH-A");
        assertTrue(found.isPresent());
        assertEquals("Vehicle Type A", found.get().getName());
    }

    @Test
    void findByCode_notFound() {
        Optional<CostCenter> found = costCenterRepository.findByCode("MISSING");
        assertTrue(found.isEmpty());
    }

    @Test
    void existsByCode_trueWhenExists() {
        costCenterRepository.save(build("VEH-A", "Vehicle Type A", "VEHICLE_TYPE", true));
        assertTrue(costCenterRepository.existsByCode("VEH-A"));
        assertFalse(costCenterRepository.existsByCode("MISSING"));
    }

    @Test
    void findByActiveTrue_returnsOnlyActive() {
        costCenterRepository.save(build("VEH-A", "Vehicle Type A", "VEHICLE_TYPE", true));
        costCenterRepository.save(build("VEH-B", "Vehicle Type B", "VEHICLE_TYPE", false));
        costCenterRepository.save(build("INS-1", "Instructor Team 1", "INSTRUCTOR_TEAM", true));

        List<CostCenter> active = costCenterRepository.findByActiveTrue();
        assertEquals(2, active.size());
    }

    @Test
    void uniqueConstraint_duplicateCode_throws() {
        costCenterRepository.save(build("VEH-A", "Vehicle Type A", "VEHICLE_TYPE", true));
        CostCenter duplicate = build("VEH-A", "Other", "VEHICLE_TYPE", true);
        assertThrows(DataIntegrityViolationException.class, () -> costCenterRepository.saveAndFlush(duplicate));
    }
}
