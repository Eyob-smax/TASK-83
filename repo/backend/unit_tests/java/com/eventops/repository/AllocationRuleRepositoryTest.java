package com.eventops.repository;

import com.eventops.domain.finance.AllocationMethod;
import com.eventops.domain.finance.AllocationRule;
import com.eventops.domain.finance.RevenueRecognitionMethod;
import com.eventops.repository.finance.AllocationRuleRepository;
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
class AllocationRuleRepositoryTest {

    @Autowired
    private AllocationRuleRepository allocationRuleRepository;

    @BeforeEach
    void clean() {
        allocationRuleRepository.deleteAll();
    }

    private AllocationRule build(String name, AllocationMethod method, boolean active) {
        AllocationRule r = new AllocationRule();
        r.setName(name);
        r.setAllocationMethod(method);
        r.setRecognitionMethod(RevenueRecognitionMethod.IMMEDIATE);
        r.setActive(active);
        r.setCreatedBy("admin");
        r.setRuleConfig("{\"weight\":1.0}");
        return r;
    }

    @Test
    void saveAndFindById_roundTrip() {
        AllocationRule saved = allocationRuleRepository.save(
                build("Rule A", AllocationMethod.PROPORTIONAL, true));
        Optional<AllocationRule> found = allocationRuleRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("Rule A", found.get().getName());
        assertEquals(1, found.get().getVersion());
    }

    @Test
    void findByActiveTrue_returnsOnlyActive() {
        allocationRuleRepository.save(build("Rule A", AllocationMethod.PROPORTIONAL, true));
        allocationRuleRepository.save(build("Rule B", AllocationMethod.FIXED, false));
        allocationRuleRepository.save(build("Rule C", AllocationMethod.TIERED, true));

        List<AllocationRule> active = allocationRuleRepository.findByActiveTrue();
        assertEquals(2, active.size());
    }

    @Test
    void findByAllocationMethod_filtersCorrectly() {
        allocationRuleRepository.save(build("Rule A", AllocationMethod.PROPORTIONAL, true));
        allocationRuleRepository.save(build("Rule B", AllocationMethod.PROPORTIONAL, true));
        allocationRuleRepository.save(build("Rule C", AllocationMethod.FIXED, true));
        allocationRuleRepository.save(build("Rule D", AllocationMethod.TIERED, true));

        assertEquals(2, allocationRuleRepository.findByAllocationMethod(AllocationMethod.PROPORTIONAL).size());
        assertEquals(1, allocationRuleRepository.findByAllocationMethod(AllocationMethod.FIXED).size());
        assertEquals(1, allocationRuleRepository.findByAllocationMethod(AllocationMethod.TIERED).size());
    }
}
