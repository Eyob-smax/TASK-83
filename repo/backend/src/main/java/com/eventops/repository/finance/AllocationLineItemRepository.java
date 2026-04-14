package com.eventops.repository.finance;

import com.eventops.domain.finance.AllocationLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AllocationLineItemRepository extends JpaRepository<AllocationLineItem, String> {
    List<AllocationLineItem> findByPostingId(String postingId);
}
