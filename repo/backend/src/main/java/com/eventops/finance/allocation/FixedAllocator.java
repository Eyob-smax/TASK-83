package com.eventops.finance.allocation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Assigns fixed amounts per cost center from ruleConfig (JSON parsed as Map&lt;costCenterId, amount&gt;).
 * <p>
 * If the sum of fixed amounts doesn't equal totalAmount, the difference is added
 * as an "UNALLOCATED" line item (when difference &gt; 0).
 */
public class FixedAllocator implements AllocationCalculator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public List<AllocationResult> allocate(BigDecimal totalAmount, Map<String, BigDecimal> weights, String ruleConfig) {
        Map<String, BigDecimal> fixedAmounts;
        try {
            fixedAmounts = OBJECT_MAPPER.readValue(ruleConfig, new TypeReference<Map<String, BigDecimal>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid ruleConfig JSON for FixedAllocator: " + e.getMessage(), e);
        }

        List<AllocationResult> results = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;

        for (Map.Entry<String, BigDecimal> entry : fixedAmounts.entrySet()) {
            String costCenterId = entry.getKey();
            BigDecimal amount = entry.getValue();
            allocated = allocated.add(amount);

            results.add(new AllocationResult(
                    costCenterId,
                    amount,
                    "Fixed allocation"
            ));
        }

        BigDecimal difference = totalAmount.subtract(allocated);
        if (difference.compareTo(BigDecimal.ZERO) > 0) {
            results.add(new AllocationResult(
                    "UNALLOCATED",
                    difference,
                    "Unallocated remainder"
            ));
        }

        return results;
    }
}
