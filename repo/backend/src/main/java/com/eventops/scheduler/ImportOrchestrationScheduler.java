package com.eventops.scheduler;

import com.eventops.domain.importing.CrawlJob;
import com.eventops.domain.importing.ImportJobStatus;
import com.eventops.domain.importing.ImportSource;
import com.eventops.repository.importing.CrawlJobRepository;
import com.eventops.repository.importing.ImportSourceRepository;
import com.eventops.service.importing.ImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Orchestrates shared-folder import crawls. Starts queued jobs
 * that are under the per-source concurrency cap.
 */
@Component
public class ImportOrchestrationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ImportOrchestrationScheduler.class);

    private final ImportService importService;
    private final ImportSourceRepository importSourceRepository;
    private final CrawlJobRepository crawlJobRepository;

    public ImportOrchestrationScheduler(ImportService importService,
                                         ImportSourceRepository importSourceRepository,
                                         CrawlJobRepository crawlJobRepository) {
        this.importService = importService;
        this.importSourceRepository = importSourceRepository;
        this.crawlJobRepository = crawlJobRepository;
    }

    @Scheduled(fixedDelayString = "${eventops.import.schedule-interval-ms:300000}")
    public void orchestrateImports() {
        List<ImportSource> activeSources = importSourceRepository.findByActiveTrue();

        for (ImportSource source : activeSources) {
            List<CrawlJob> sourceQueued = crawlJobRepository
                .findBySourceIdAndStatusOrderByPriorityAscCreatedAtAsc(source.getId(), ImportJobStatus.QUEUED);

            for (CrawlJob job : sourceQueued) {
                try {
                    importService.startIfUnderCap(job);
                    if (job.getStatus() == ImportJobStatus.RUNNING) {
                        importService.executeCrawl(job);
                    }
                } catch (Exception e) {
                    log.error("Failed to process import job {} for source {}: {}",
                            job.getId(), source.getName(), e.getMessage());
                }
            }
        }
    }
}
