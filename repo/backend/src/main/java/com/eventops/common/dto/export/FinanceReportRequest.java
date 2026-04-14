package com.eventops.common.dto.export;

import jakarta.validation.constraints.NotBlank;

public class FinanceReportRequest {

    @NotBlank
    private String periodId;

    public String getPeriodId() { return periodId; }
    public void setPeriodId(String periodId) { this.periodId = periodId; }
}
