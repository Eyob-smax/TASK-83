package com.eventops.finance;

import com.eventops.domain.finance.AllocationRule;
import com.eventops.domain.finance.AllocationMethod;
import com.eventops.domain.finance.RevenueRecognitionMethod;
import com.eventops.finance.rules.RuleVersionManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RuleVersionTest {

    private final RuleVersionManager versionManager = new RuleVersionManager();

    @Test
    void createNewVersion_incrementsVersion() {
        AllocationRule rule = new AllocationRule();
        rule.setName("Test Rule");
        rule.setVersion(3);
        rule.setAllocationMethod(AllocationMethod.PROPORTIONAL);
        rule.setRecognitionMethod(RevenueRecognitionMethod.IMMEDIATE);

        AllocationRule newVersion = versionManager.createNewVersion(rule);
        assertEquals(4, newVersion.getVersion());
    }

    @Test
    void createNewVersion_copiesAllFields() {
        AllocationRule rule = new AllocationRule();
        rule.setName("My Rule");
        rule.setAllocationMethod(AllocationMethod.FIXED);
        rule.setRecognitionMethod(RevenueRecognitionMethod.OVER_SESSION_DATES);
        rule.setAccountId("acc-1");
        rule.setCostCenterId("cc-1");
        rule.setRuleConfig("{\"key\":\"value\"}");
        rule.setVersion(1);

        AllocationRule newVersion = versionManager.createNewVersion(rule);
        assertEquals("My Rule", newVersion.getName());
        assertEquals(AllocationMethod.FIXED, newVersion.getAllocationMethod());
        assertEquals("acc-1", newVersion.getAccountId());
        assertEquals("{\"key\":\"value\"}", newVersion.getRuleConfig());
        assertTrue(newVersion.isActive());
    }

    @Test
    void getNextVersion_returnsIncrementedValue() {
        AllocationRule rule = new AllocationRule();
        rule.setVersion(5);
        assertEquals(6, versionManager.getNextVersion(rule));
    }
}
