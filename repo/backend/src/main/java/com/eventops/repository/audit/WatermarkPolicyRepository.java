package com.eventops.repository.audit;

import com.eventops.domain.audit.WatermarkPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WatermarkPolicyRepository extends JpaRepository<WatermarkPolicy, String> {
    List<WatermarkPolicy> findByReportTypeAndRoleType(String reportType, String roleType);
    List<WatermarkPolicy> findByActiveTrue();
}
