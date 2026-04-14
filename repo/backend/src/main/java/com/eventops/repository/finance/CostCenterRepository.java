package com.eventops.repository.finance;

import com.eventops.domain.finance.CostCenter;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface CostCenterRepository extends JpaRepository<CostCenter, String> {
    Optional<CostCenter> findByCode(String code);
    boolean existsByCode(String code);
    List<CostCenter> findByActiveTrue();
}
