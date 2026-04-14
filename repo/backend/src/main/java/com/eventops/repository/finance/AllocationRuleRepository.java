package com.eventops.repository.finance;

import com.eventops.domain.finance.AllocationRule;
import com.eventops.domain.finance.AllocationMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AllocationRuleRepository extends JpaRepository<AllocationRule, String> {
    List<AllocationRule> findByActiveTrue();
    List<AllocationRule> findByAllocationMethod(AllocationMethod method);
}
