package com.eventops.finance.allocation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface AllocationCalculator {
    List<AllocationResult> allocate(BigDecimal totalAmount, Map<String, BigDecimal> weights, String ruleConfig);
}
