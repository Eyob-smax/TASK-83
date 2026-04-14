package com.eventops.repository.importing;

import com.eventops.domain.importing.CrawlJob;
import com.eventops.domain.importing.ImportJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;
import java.util.Optional;

public interface CrawlJobRepository extends JpaRepository<CrawlJob, String>, JpaSpecificationExecutor<CrawlJob> {
    List<CrawlJob> findBySourceId(String sourceId);
    List<CrawlJob> findByStatus(ImportJobStatus status);
    List<CrawlJob> findBySourceIdAndStatusOrderByPriorityAscCreatedAtAsc(String sourceId, ImportJobStatus status);
    Optional<CrawlJob> findByTraceId(String traceId);
    long countBySourceIdAndStatus(String sourceId, ImportJobStatus status);
}
