package com.eventops.service;

import com.eventops.audit.logging.AuditService;
import com.eventops.common.exception.BusinessException;
import com.eventops.domain.audit.ExportJob;
import com.eventops.domain.audit.ExportStatus;
import com.eventops.domain.user.RoleType;
import com.eventops.domain.user.User;
import com.eventops.repository.audit.AuditEventRepository;
import com.eventops.repository.audit.ExportJobRepository;
import com.eventops.repository.audit.FieldDiffRepository;
import com.eventops.repository.audit.WatermarkPolicyRepository;
import com.eventops.repository.checkin.CheckInRecordRepository;
import com.eventops.repository.finance.AllocationLineItemRepository;
import com.eventops.repository.finance.AllocationRuleRepository;
import com.eventops.repository.finance.PostingJournalRepository;
import com.eventops.repository.registration.RegistrationRepository;
import com.eventops.repository.user.UserRepository;
import com.eventops.service.audit.ExportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportDownloadAuthorizationTest {

    @Mock
    private ExportJobRepository exportJobRepository;
    @Mock
    private WatermarkPolicyRepository watermarkPolicyRepository;
    @Mock
    private AuditEventRepository auditEventRepository;
    @Mock
    private FieldDiffRepository fieldDiffRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private RegistrationRepository registrationRepository;
    @Mock
    private CheckInRecordRepository checkInRecordRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PostingJournalRepository postingJournalRepository;
    @Mock
    private AllocationLineItemRepository allocationLineItemRepository;
    @Mock
    private AllocationRuleRepository allocationRuleRepository;

    @InjectMocks
    private ExportService exportService;

    private Path tempFile;

    @BeforeEach
    void setUp() throws IOException {
        tempFile = Files.createTempFile("export-authz", ".csv");
        Files.writeString(tempFile, "id,value\n1,ok\n", StandardCharsets.UTF_8);
        lenient().when(auditEventRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempFile != null) {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void downloadExport_deniesNonOwnerWithoutAdminRole() {
        ExportJob job = completedJob("export-1", "owner-1", "ROSTER", tempFile.toString());
        when(exportJobRepository.findById("export-1")).thenReturn(Optional.of(job));
        when(userRepository.findById("other-1")).thenReturn(Optional.of(user("other-1", RoleType.EVENT_STAFF)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> exportService.downloadExport("export-1", "other-1"));

        assertEquals("EXPORT_SCOPE_DENIED", ex.getErrorCode());
    }

    @Test
    void downloadExport_deniesRoleWithoutExportTypePermission() {
        ExportJob job = completedJob("export-2", "owner-finance", "FINANCE_REPORT", tempFile.toString());
        when(exportJobRepository.findById("export-2")).thenReturn(Optional.of(job));
        when(userRepository.findById("owner-finance")).thenReturn(Optional.of(user("owner-finance", RoleType.EVENT_STAFF)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> exportService.downloadExport("export-2", "owner-finance"));

        assertEquals("EXPORT_ROLE_DENIED", ex.getErrorCode());
    }

    @Test
    void downloadExport_allowsOwnerWithPermittedRole() throws IOException {
        ExportJob job = completedJob("export-3", "owner-staff", "ROSTER", tempFile.toString());
        when(exportJobRepository.findById("export-3")).thenReturn(Optional.of(job));
        when(userRepository.findById("owner-staff")).thenReturn(Optional.of(user("owner-staff", RoleType.EVENT_STAFF)));
        when(watermarkPolicyRepository.findByReportTypeAndRoleType("ROSTER", "EVENT_STAFF")).thenReturn(List.of());

        byte[] data = exportService.downloadExport("export-3", "owner-staff");

        assertArrayEquals(Files.readAllBytes(tempFile), data);
    }

    private ExportJob completedJob(String id, String owner, String exportType, String path) {
        ExportJob job = new ExportJob();
        job.setId(id);
        job.setRequestedBy(owner);
        job.setExportType(exportType);
        job.setStatus(ExportStatus.COMPLETED);
        job.setFilePath(path);
        return job;
    }

    private User user(String id, RoleType roleType) {
        User user = new User();
        user.setId(id);
        user.setUsername(id);
        user.setDisplayName(id);
        user.setRoleType(roleType);
        return user;
    }
}
