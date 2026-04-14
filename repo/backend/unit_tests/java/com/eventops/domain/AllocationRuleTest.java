package com.eventops.domain;

import com.eventops.domain.finance.AllocationRule;
import com.eventops.domain.finance.AllocationMethod;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AllocationRuleTest {

    @Test
    void defaultVersion_is1() {
        AllocationRule rule = new AllocationRule();
        assertEquals(1, rule.getVersion());
    }

    @Test
    void defaultActive_isTrue() {
        AllocationRule rule = new AllocationRule();
        assertTrue(rule.isActive());
    }
}
