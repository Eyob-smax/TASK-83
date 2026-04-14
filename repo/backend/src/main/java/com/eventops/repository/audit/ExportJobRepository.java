package com.eventops.repository.audit;

import com.eventops.domain.audit.ExportJob;
import com.eventops.domain.audit.ExportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExportJobRepository extends JpaRepository<ExportJob, String> {
    List<ExportJob> findByRequestedBy(String requestedBy);
    List<ExportJob> findByStatus(ExportStatus status);
}
