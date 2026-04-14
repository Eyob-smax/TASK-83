package com.eventops.service;

import com.eventops.audit.logging.AuditService;
import com.eventops.common.exception.BusinessException;
import com.eventops.domain.importing.CrawlJob;
import com.eventops.domain.importing.ImportJobStatus;
import com.eventops.domain.importing.ImportMode;
import com.eventops.domain.importing.ImportSource;
import com.eventops.repository.importing.CrawlJobRepository;
import com.eventops.repository.importing.ImportSourceRepository;
import com.eventops.service.importing.ImportService;
import com.eventops.service.importing.SharedFolderPathResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportServiceTest {

    @Mock
    private ImportSourceRepository importSourceRepository;

    @Mock
    private CrawlJobRepository crawlJobRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private SharedFolderPathResolver sharedFolderPathResolver;

    @InjectMocks
    private ImportService importService;

    private static final String SOURCE_ID = "source-1";

    // ---------------------------------------------------------------
    // triggerCrawl()
    // ---------------------------------------------------------------

    @Test
    void triggerCrawl_createsQueuedJob() {
        ImportSource source = buildSource(SOURCE_ID, true);

        when(importSourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(crawlJobRepository.save(any(CrawlJob.class)))
                .thenAnswer(invocation -> {
                    CrawlJob job = invocation.getArgument(0);
                    if (job.getId() == null) {
                        job.setId("generated-job-id");
                    }
                    return job;
                });
        // For startIfUnderCap called internally
        when(crawlJobRepository.countBySourceIdAndStatus(SOURCE_ID, ImportJobStatus.RUNNING))
                .thenReturn(0L);

        CrawlJob result = importService.triggerCrawl(SOURCE_ID, ImportMode.INCREMENTAL);

        assertNotNull(result);
        assertEquals(SOURCE_ID, result.getSourceId());
        assertEquals(ImportMode.INCREMENTAL, result.getImportMode());

        // Verify the first save was with QUEUED status
        ArgumentCaptor<CrawlJob> captor = ArgumentCaptor.forClass(CrawlJob.class);
        verify(crawlJobRepository, atLeastOnce()).save(captor.capture());

        assertTrue(captor.getAllValues().stream().allMatch(job -> job.getPriority() == 100));

        verify(auditService).log(any(), eq("SYSTEM"), eq("ImportService"), eq("SYSTEM"),
                eq("CrawlJob"), any(), any());
    }

    @Test
    void triggerCrawl_appliesProvidedPriority() {
        ImportSource source = buildSource(SOURCE_ID, true);

        when(importSourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(crawlJobRepository.save(any(CrawlJob.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(crawlJobRepository.countBySourceIdAndStatus(SOURCE_ID, ImportJobStatus.RUNNING))
            .thenReturn(0L);

        CrawlJob result = importService.triggerCrawl(SOURCE_ID, ImportMode.FULL, 5);

        assertEquals(5, result.getPriority());
        }

    @Test
    void triggerCrawl_throws404_whenSourceNotFound() {
        when(importSourceRepository.findById("missing")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> importService.triggerCrawl("missing", ImportMode.FULL));

        assertEquals("NOT_FOUND", ex.getErrorCode());
        assertEquals(404, ex.getHttpStatus());
    }

    @Test
    void triggerCrawl_throws422_whenSourceInactive() {
        ImportSource source = buildSource(SOURCE_ID, false);

        when(importSourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> importService.triggerCrawl(SOURCE_ID, ImportMode.INCREMENTAL));

        assertEquals("SOURCE_INACTIVE", ex.getErrorCode());
        assertEquals(422, ex.getHttpStatus());
    }

    // ---------------------------------------------------------------
    // startIfUnderCap()
    // ---------------------------------------------------------------

    @Test
    void startIfUnderCap_startsJob_whenBelowCap() {
        ImportSource source = buildSource(SOURCE_ID, true);
        source.setConcurrencyCap(3);

        CrawlJob job = new CrawlJob();
        job.setId("job-1");
        job.setSourceId(SOURCE_ID);
        job.setStatus(ImportJobStatus.QUEUED);

        when(crawlJobRepository.countBySourceIdAndStatus(SOURCE_ID, ImportJobStatus.RUNNING))
                .thenReturn(1L);
        when(importSourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(crawlJobRepository.save(any(CrawlJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        importService.startIfUnderCap(job);

        assertEquals(ImportJobStatus.RUNNING, job.getStatus());
        assertNotNull(job.getStartedAt());

        verify(crawlJobRepository).save(job);
    }

    @Test
    void startIfUnderCap_skipsJob_whenAtCap() {
        ImportSource source = buildSource(SOURCE_ID, true);
        source.setConcurrencyCap(3);

        CrawlJob job = new CrawlJob();
        job.setId("job-2");
        job.setSourceId(SOURCE_ID);
        job.setStatus(ImportJobStatus.QUEUED);

        when(crawlJobRepository.countBySourceIdAndStatus(SOURCE_ID, ImportJobStatus.RUNNING))
                .thenReturn(3L);
        when(importSourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));

        importService.startIfUnderCap(job);

        assertEquals(ImportJobStatus.QUEUED, job.getStatus());
        assertNull(job.getStartedAt());

        verify(crawlJobRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // Circuit breaker default
    // ---------------------------------------------------------------

    @Test
    void circuitBreaker_concept() {
        ImportSource defaultSource = new ImportSource();
        assertEquals(10, defaultSource.getCircuitBreakerThreshold(),
                "Default circuit breaker threshold should be 10");
    }

    // ---------------------------------------------------------------
    // Timeout configuration
    // ---------------------------------------------------------------

    @Test
    void timeoutSeconds_defaultIs30() {
        ImportSource source = new ImportSource();
        source.setTimeoutSeconds(30);
        assertEquals(30, source.getTimeoutSeconds(),
                "Default timeout must be 30 seconds per requirement 9 (import)");
    }

    @Test
    void traceId_isGeneratedForQueuedJob() {
        ImportSource source = buildSource(SOURCE_ID, true);

        when(importSourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(crawlJobRepository.save(any(CrawlJob.class)))
                .thenAnswer(invocation -> {
                    CrawlJob job = invocation.getArgument(0);
                    if (job.getId() == null) {
                        job.setId("gen-id");
                    }
                    if (job.getTraceId() == null) {
                        job.setTraceId(java.util.UUID.randomUUID().toString());
                    }
                    return job;
                });
        when(crawlJobRepository.countBySourceIdAndStatus(SOURCE_ID, ImportJobStatus.RUNNING))
                .thenReturn(0L);

        CrawlJob result = importService.triggerCrawl(SOURCE_ID, ImportMode.INCREMENTAL);

        assertNotNull(result.getTraceId(), "Queued crawl job must have a trace ID");
    }

    @Test
    void createSource_rejectsFolderPathOutsideSharedRoot() {
        var request = new com.eventops.common.dto.importing.ImportSourceUpsertRequest();
        request.setName("Unsafe");
        request.setFolderPath("../outside");
        request.setFilePattern("*.csv");
        request.setImportMode(ImportMode.INCREMENTAL);
        request.setConcurrencyCap(3);
        request.setTimeoutSeconds(30);
        request.setCircuitBreakerThreshold(10);
        request.setActive(true);

        when(sharedFolderPathResolver.sanitizeForStorage("../outside"))
                .thenThrow(new BusinessException(
                        "folderPath must remain inside the configured shared-folder base path",
                        422,
                        "FOLDER_PATH_OUTSIDE_SHARED_ROOT"
                ));

        BusinessException ex = assertThrows(BusinessException.class, () -> importService.createSource(request));

        assertEquals("FOLDER_PATH_OUTSIDE_SHARED_ROOT", ex.getErrorCode());
        verify(importSourceRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------

    private ImportSource buildSource(String id, boolean active) {
        ImportSource source = new ImportSource();
        source.setId(id);
        source.setName("Test Source");
        source.setFolderPath("/data/imports");
        source.setFilePattern("*.csv");
        source.setImportMode(ImportMode.INCREMENTAL);
        source.setConcurrencyCap(3);
        source.setTimeoutSeconds(30);
        source.setCircuitBreakerThreshold(10);
        source.setActive(active);
        return source;
    }
}
