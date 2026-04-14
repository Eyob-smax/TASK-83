package com.eventops.finance.allocation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Distributes totalAmount across cost centers by weight proportions.
 * <p>
 * If weights are {A: 3, B: 7}, A gets 30% and B gets 70%.
 * Uses BigDecimal with HALF_UP rounding to 2 decimal places.
 * The last entry absorbs the rounding remainder so the sum exactly equals totalAmount.
 */
public class ProportionalAllocator implements AllocationCalculator {

    @Override
    public List<AllocationResult> allocate(BigDecimal totalAmount, Map<String, BigDecimal> weights, String ruleConfig) {
        if (weights == null || weights.isEmpty()) {
            return List.of();
        }

        BigDecimal totalWeight = weights.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            return List.of();
        }

        List<AllocationResult> results = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        List<String> keys = new ArrayList<>(weights.keySet());

        for (int i = 0; i < keys.size(); i++) {
            String costCenterId = keys.get(i);
            BigDecimal weight = weights.get(costCenterId);

            BigDecimal amount;
            if (i == keys.size() - 1) {
                // Last entry absorbs the rounding remainder
                amount = totalAmount.subtract(allocated);
            } else {
                amount = totalAmount.multiply(weight)
                        .divide(totalWeight, 2, RoundingMode.HALF_UP);
                allocated = allocated.add(amount);
            }

            BigDecimal percentage = weight.multiply(BigDecimal.valueOf(100))
                    .divide(totalWeight, 2, RoundingMode.HALF_UP);

            results.add(new AllocationResult(
                    costCenterId,
                    amount,
                    "Proportional allocation: " + percentage + "% of total"
            ));
        }

        return results;
    }
}
