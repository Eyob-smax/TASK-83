package com.eventops.repository.audit;

import com.eventops.domain.audit.FieldDiff;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FieldDiffRepository extends JpaRepository<FieldDiff, String> {
    List<FieldDiff> findByAuditEventId(String auditEventId);
}
