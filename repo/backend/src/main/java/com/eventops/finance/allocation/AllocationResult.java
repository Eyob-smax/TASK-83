package com.eventops.finance.allocation;

import java.math.BigDecimal;

public record AllocationResult(
    String costCenterId,
    BigDecimal amount,
    String description
) {}
