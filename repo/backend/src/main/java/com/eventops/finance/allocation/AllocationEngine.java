package com.eventops.finance.allocation;

import com.eventops.domain.finance.AllocationMethod;

public class AllocationEngine {
    private final ProportionalAllocator proportional = new ProportionalAllocator();
    private final FixedAllocator fixed = new FixedAllocator();
    private final TieredAllocator tiered = new TieredAllocator();

    public AllocationCalculator getCalculator(AllocationMethod method) {
        return switch (method) {
            case PROPORTIONAL -> proportional;
            case FIXED -> fixed;
            case TIERED -> tiered;
        };
    }
}
