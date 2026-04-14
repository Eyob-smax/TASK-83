package com.eventops.finance.allocation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Applies tiered thresholds from ruleConfig (JSON array of tiers with "upTo" and "rate" fields).
 * <p>
 * Each tier defines a ceiling and an allocation rate. For example:
 * <pre>
 * [
 *   {"upTo": 1000, "rate": 0.10},
 *   {"upTo": 5000, "rate": 0.15},
 *   {"upTo": null, "rate": 0.20}
 * ]
 * </pre>
 * The allocator walks through the totalAmount tier by tier, computing the allocation for
 * the portion of the amount that falls within each tier. Each crossed tier produces a
 * single AllocationResult. The cost center ID is taken from the first entry in the weights map.
 */
public class TieredAllocator implements AllocationCalculator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public List<AllocationResult> allocate(BigDecimal totalAmount, Map<String, BigDecimal> weights, String ruleConfig) {
        JsonNode tiersNode;
        try {
            tiersNode = OBJECT_MAPPER.readTree(ruleConfig);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid ruleConfig JSON for TieredAllocator: " + e.getMessage(), e);
        }

        if (!tiersNode.isArray()) {
            throw new IllegalArgumentException("ruleConfig must be a JSON array of tier objects");
        }

        // Use the first weight entry's key as the cost center ID (simplified)
        String costCenterId = (weights != null && !weights.isEmpty())
                ? weights.keySet().iterator().next()
                : "DEFAULT";

        List<AllocationResult> results = new ArrayList<>();
        BigDecimal remaining = totalAmount;
        BigDecimal previousCeiling = BigDecimal.ZERO;

        for (JsonNode tierNode : tiersNode) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal rate = tierNode.get("rate").decimalValue();
            JsonNode upToNode = tierNode.get("upTo");

            BigDecimal tierCeiling;
            boolean isUnbounded;
            if (upToNode == null || upToNode.isNull()) {
                // Unbounded final tier — applies to all remaining amount
                tierCeiling = totalAmount;
                isUnbounded = true;
            } else {
                tierCeiling = upToNode.decimalValue();
                isUnbounded = false;
            }

            // The portion of totalAmount that falls within this tier
            BigDecimal tierWidth = tierCeiling.subtract(previousCeiling);
            if (tierWidth.compareTo(BigDecimal.ZERO) <= 0 && !isUnbounded) {
                previousCeiling = tierCeiling;
                continue;
            }

            BigDecimal applicableAmount;
            if (isUnbounded) {
                applicableAmount = remaining;
            } else {
                applicableAmount = remaining.min(tierWidth);
            }

            BigDecimal tierAllocation = applicableAmount.multiply(rate)
                    .setScale(2, RoundingMode.HALF_UP);

            String tierDescription;
            if (isUnbounded) {
                tierDescription = String.format("Tier above %s at rate %s", previousCeiling, rate);
            } else {
                tierDescription = String.format("Tier %s-%s at rate %s", previousCeiling, tierCeiling, rate);
            }

            results.add(new AllocationResult(
                    costCenterId,
                    tierAllocation,
                    tierDescription
            ));

            remaining = remaining.subtract(applicableAmount);
            previousCeiling = tierCeiling;
        }

        return results;
    }
}
