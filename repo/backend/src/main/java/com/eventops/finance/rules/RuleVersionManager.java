package com.eventops.finance.rules;

import com.eventops.domain.finance.AllocationRule;

public class RuleVersionManager {

    public AllocationRule createNewVersion(AllocationRule existingRule) {
        AllocationRule newVersion = new AllocationRule();
        newVersion.setName(existingRule.getName());
        newVersion.setAllocationMethod(existingRule.getAllocationMethod());
        newVersion.setRecognitionMethod(existingRule.getRecognitionMethod());
        newVersion.setAccountId(existingRule.getAccountId());
        newVersion.setCostCenterId(existingRule.getCostCenterId());
        newVersion.setRuleConfig(existingRule.getRuleConfig());
        newVersion.setVersion(existingRule.getVersion() + 1);
        newVersion.setActive(true);
        newVersion.setCreatedBy(existingRule.getCreatedBy());
        return newVersion;
    }

    public int getNextVersion(AllocationRule existingRule) {
        return existingRule.getVersion() + 1;
    }
}
