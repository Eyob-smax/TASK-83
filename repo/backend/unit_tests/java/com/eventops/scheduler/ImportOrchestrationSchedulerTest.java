package com.eventops.scheduler;

import com.eventops.domain.importing.CrawlJob;
import com.eventops.domain.importing.ImportJobStatus;
import com.eventops.domain.importing.ImportSource;
import com.eventops.repository.importing.CrawlJobRepository;
import com.eventops.repository.importing.ImportSourceRepository;
import com.eventops.service.importing.ImportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportOrchestrationSchedulerTest {

    @Mock
    private ImportService importService;

    @Mock
    private ImportSourceRepository importSourceRepository;

    @Mock
    private CrawlJobRepository crawlJobRepository;

    @InjectMocks
    private ImportOrchestrationScheduler scheduler;

    @Test
    void orchestrateImports_processesQueuedJobsInRepositoryPriorityOrder() {
        ImportSource source = new ImportSource();
        source.setId("source-1");
        source.setName("Primary Source");
        when(importSourceRepository.findByActiveTrue()).thenReturn(List.of(source));

        CrawlJob highPriority = new CrawlJob();
        highPriority.setId("job-high");
        highPriority.setSourceId("source-1");
        highPriority.setStatus(ImportJobStatus.QUEUED);
        highPriority.setPriority(1);

        CrawlJob lowPriority = new CrawlJob();
        lowPriority.setId("job-low");
        lowPriority.setSourceId("source-1");
        lowPriority.setStatus(ImportJobStatus.QUEUED);
        lowPriority.setPriority(100);

        when(crawlJobRepository.findBySourceIdAndStatusOrderByPriorityAscCreatedAtAsc(
                "source-1", ImportJobStatus.QUEUED))
                .thenReturn(List.of(highPriority, lowPriority));

        doAnswer(invocation -> {
            CrawlJob job = invocation.getArgument(0);
            job.setStatus(ImportJobStatus.RUNNING);
            return null;
        }).when(importService).startIfUnderCap(any(CrawlJob.class));

        scheduler.orchestrateImports();

        InOrder inOrder = inOrder(importService);
        inOrder.verify(importService).startIfUnderCap(highPriority);
        inOrder.verify(importService).executeCrawl(highPriority);
        inOrder.verify(importService).startIfUnderCap(lowPriority);
        inOrder.verify(importService).executeCrawl(lowPriority);

        verify(crawlJobRepository)
                .findBySourceIdAndStatusOrderByPriorityAscCreatedAtAsc("source-1", ImportJobStatus.QUEUED);
    }
}
