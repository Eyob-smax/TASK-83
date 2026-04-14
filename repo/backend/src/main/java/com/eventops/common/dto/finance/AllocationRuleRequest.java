package com.eventops.common.dto.finance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class AllocationRuleRequest {
    @NotBlank private String name;
    @NotNull private String allocationMethod;
    @NotNull private String recognitionMethod;
    private String accountId;
    private String costCenterId;
    private String ruleConfig;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAllocationMethod() { return allocationMethod; }
    public void setAllocationMethod(String allocationMethod) { this.allocationMethod = allocationMethod; }
    public String getRecognitionMethod() { return recognitionMethod; }
    public void setRecognitionMethod(String recognitionMethod) { this.recognitionMethod = recognitionMethod; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getCostCenterId() { return costCenterId; }
    public void setCostCenterId(String costCenterId) { this.costCenterId = costCenterId; }
    public String getRuleConfig() { return ruleConfig; }
    public void setRuleConfig(String ruleConfig) { this.ruleConfig = ruleConfig; }
}
