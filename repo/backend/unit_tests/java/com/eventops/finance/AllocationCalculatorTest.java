package com.eventops.finance;

import com.eventops.finance.allocation.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AllocationCalculatorTest {

    @Test
    void proportional_distributes_byWeight() {
        ProportionalAllocator alloc = new ProportionalAllocator();
        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        weights.put("CC-A", new BigDecimal("3"));
        weights.put("CC-B", new BigDecimal("7"));
        List<AllocationResult> results = alloc.allocate(new BigDecimal("1000.00"), weights, null);
        assertEquals(2, results.size());
        assertEquals(new BigDecimal("300.00"), results.get(0).amount());
        assertEquals(new BigDecimal("700.00"), results.get(1).amount());
    }

    @Test
    void proportional_roundingRemainder_adjustedToLast() {
        ProportionalAllocator alloc = new ProportionalAllocator();
        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        weights.put("CC-A", new BigDecimal("1"));
        weights.put("CC-B", new BigDecimal("1"));
        weights.put("CC-C", new BigDecimal("1"));
        List<AllocationResult> results = alloc.allocate(new BigDecimal("100.00"), weights, null);
        BigDecimal sum = results.stream().map(AllocationResult::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("100.00"), sum, "Sum must exactly equal totalAmount");
    }

    @Test
    void proportional_singleEntry_getsFullAmount() {
        ProportionalAllocator alloc = new ProportionalAllocator();
        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        weights.put("CC-A", new BigDecimal("1"));
        List<AllocationResult> results = alloc.allocate(new BigDecimal("5000.00"), weights, null);
        assertEquals(1, results.size());
        assertEquals(new BigDecimal("5000.00"), results.get(0).amount());
    }

    @Test
    void fixed_allocates_configuredAmounts() {
        FixedAllocator alloc = new FixedAllocator();
        String config = "{\"CC-A\":\"400.00\",\"CC-B\":\"600.00\"}";
        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        weights.put("CC-A", BigDecimal.ONE);
        weights.put("CC-B", BigDecimal.ONE);
        List<AllocationResult> results = alloc.allocate(new BigDecimal("1000.00"), weights, config);
        assertEquals(2, results.size());
        assertEquals(new BigDecimal("400.00"), results.get(0).amount());
        assertEquals(new BigDecimal("600.00"), results.get(1).amount());
    }

    @Test
    void fixed_unallocatedRemainder_addedAsLineItem() {
        FixedAllocator alloc = new FixedAllocator();
        String config = "{\"CC-A\":\"300.00\"}";
        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        weights.put("CC-A", BigDecimal.ONE);
        List<AllocationResult> results = alloc.allocate(new BigDecimal("1000.00"), weights, config);
        assertTrue(results.size() >= 2, "Should have unallocated remainder");
        BigDecimal sum = results.stream().map(AllocationResult::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("1000.00"), sum);
    }

    @Test
    void tiered_applies_rates_correctly() {
        TieredAllocator alloc = new TieredAllocator();
        String config = "[{\"upTo\":1000,\"rate\":0.10},{\"upTo\":5000,\"rate\":0.15},{\"upTo\":null,\"rate\":0.20}]";
        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        weights.put("CC-A", BigDecimal.ONE);
        List<AllocationResult> results = alloc.allocate(new BigDecimal("3000.00"), weights, config);
        assertFalse(results.isEmpty());
        // Tier 1: 1000 * 0.10 = 100. Tier 2: 2000 * 0.15 = 300. Total = 400
        BigDecimal totalAllocated = results.stream().map(AllocationResult::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("400.00"), totalAllocated);
    }

    @Test
    void engine_resolvesCorrectCalculator() {
        AllocationEngine engine = new AllocationEngine();
        assertTrue(engine.getCalculator(com.eventops.domain.finance.AllocationMethod.PROPORTIONAL) instanceof ProportionalAllocator);
        assertTrue(engine.getCalculator(com.eventops.domain.finance.AllocationMethod.FIXED) instanceof FixedAllocator);
        assertTrue(engine.getCalculator(com.eventops.domain.finance.AllocationMethod.TIERED) instanceof TieredAllocator);
    }
}
