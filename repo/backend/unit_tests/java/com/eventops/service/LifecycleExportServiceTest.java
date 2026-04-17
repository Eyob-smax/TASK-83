package com.eventops.service;

import com.eventops.common.exception.BusinessException;
import com.eventops.domain.audit.ExportJob;
import com.eventops.domain.audit.WatermarkPolicy;
import com.eventops.domain.user.User;
import com.eventops.repository.audit.ExportJobRepository;
import com.eventops.repository.user.UserRepository;
import com.eventops.service.export.ExportService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LifecycleExportServiceTest {

    @Mock ExportJobRepository exportJobRepository;
    @Mock UserRepository userRepository;
    @Mock com.eventops.service.audit.ExportService auditExportService;
    @Mock ObjectMapper objectMapper;

    private ExportService service;

    @BeforeEach
    void setup() {
        service = new ExportService(exportJobRepository, userRepository, auditExportService, objectMapper);
    }

    private User user(String id, String display) {
        User u = new User();
        u.setId(id);
        u.setUsername("u-" + id);
        u.setDisplayName(display);
        return u;
    }

    @Test
    void exportAuditLogs_delegatesWithSerializedFilter() throws JsonProcessingException {
        Map<String, Object> filter = new HashMap<>();
        filter.put("actionType", "LOGIN_SUCCESS");

        when(userRepository.findById("u1")).thenReturn(Optional.of(user("u1", "Alice")));
        when(objectMapper.writeValueAsString(filter)).thenReturn("{\"actionType\":\"LOGIN_SUCCESS\"}");
        ExportJob job = new ExportJob();
        when(auditExportService.generateAuditExport(eq("u1"), eq("Alice"), anyString())).thenReturn(job);

        ExportJob result = service.exportAuditLogs(filter, "u1");

        assertSame(job, result);
        verify(auditExportService).generateAuditExport(eq("u1"), eq("Alice"), eq("{\"actionType\":\"LOGIN_SUCCESS\"}"));
    }

    @Test
    void exportAuditLogs_nullFilter_passesNullToAuditService() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("u1", "Alice")));
        when(auditExportService.generateAuditExport(eq("u1"), eq("Alice"), isNull())).thenReturn(new ExportJob());

        service.exportAuditLogs(null, "u1");

        verify(auditExportService).generateAuditExport(eq("u1"), eq("Alice"), isNull());
    }

    @Test
    void exportAuditLogs_emptyFilter_passesNullToAuditService() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("u1", "Alice")));
        when(auditExportService.generateAuditExport(eq("u1"), eq("Alice"), isNull())).thenReturn(new ExportJob());

        service.exportAuditLogs(new HashMap<>(), "u1");

        verify(auditExportService).generateAuditExport(eq("u1"), eq("Alice"), isNull());
    }

    @Test
    void exportAuditLogs_serializationFails_fallsBackToToString() throws JsonProcessingException {
        Map<String, Object> filter = new HashMap<>();
        filter.put("k", "v");

        when(userRepository.findById("u1")).thenReturn(Optional.of(user("u1", "Alice")));
        when(objectMapper.writeValueAsString(filter)).thenThrow(new JsonProcessingException("boom") {});
        when(auditExportService.generateAuditExport(eq("u1"), eq("Alice"), anyString())).thenReturn(new ExportJob());

        service.exportAuditLogs(filter, "u1");

        verify(auditExportService).generateAuditExport(eq("u1"), eq("Alice"), eq(filter.toString()));
    }

    @Test
    void exportAuditLogs_userNotFound_resolvesNameToUserId() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());
        when(auditExportService.generateAuditExport(eq("missing"), eq("missing"), isNull())).thenReturn(new ExportJob());

        service.exportAuditLogs(null, "missing");

        verify(auditExportService).generateAuditExport(eq("missing"), eq("missing"), isNull());
    }

    @Test
    void exportAuditLogs_userWithoutDisplayName_usesUsername() {
        User u = user("u1", null);
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        when(auditExportService.generateAuditExport(eq("u1"), eq("u-u1"), isNull())).thenReturn(new ExportJob());

        service.exportAuditLogs(null, "u1");

        verify(auditExportService).generateAuditExport(eq("u1"), eq("u-u1"), isNull());
    }

    @Test
    void generateRosterExport_delegates() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("u1", "Alice")));
        ExportJob job = new ExportJob();
        when(auditExportService.generateRosterExport("s1", "u1", "Alice")).thenReturn(job);

        ExportJob result = service.generateRosterExport("s1", "u1");

        assertSame(job, result);
    }

    @Test
    void generateFinanceReport_delegates() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("u1", "Alice")));
        ExportJob job = new ExportJob();
        when(auditExportService.generateFinanceReport("p1", "u1", "Alice")).thenReturn(job);

        ExportJob result = service.generateFinanceReport("p1", "u1");

        assertSame(job, result);
    }

    @Test
    void downloadExport_delegates() {
        byte[] bytes = "csv,data".getBytes();
        when(auditExportService.downloadExport("exp-1", "u1")).thenReturn(bytes);

        byte[] result = service.downloadExport("exp-1", "u1");

        assertArrayEquals(bytes, result);
    }

    @Test
    void getExportFileName_extractsFileNameFromPath() {
        ExportJob job = new ExportJob();
        job.setId("exp-1");
        job.setFilePath("/var/exports/audit_2026.csv");
        when(exportJobRepository.findById("exp-1")).thenReturn(Optional.of(job));

        String fileName = service.getExportFileName("exp-1");

        assertEquals("audit_2026.csv", fileName);
    }

    @Test
    void getExportFileName_nullPathFallsBackToDefault() {
        ExportJob job = new ExportJob();
        job.setId("exp-1");
        job.setFilePath(null);
        when(exportJobRepository.findById("exp-1")).thenReturn(Optional.of(job));

        String fileName = service.getExportFileName("exp-1");

        assertEquals("export_exp-1.dat", fileName);
    }

    @Test
    void getExportFileName_jobNotFound_throws404() {
        when(exportJobRepository.findById("missing")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getExportFileName("missing"));
        assertEquals(404, ex.getHttpStatus());
    }

    @Test
    void getExportPolicies_delegates() {
        WatermarkPolicy policy = new WatermarkPolicy();
        when(auditExportService.getExportPolicies()).thenReturn(List.of(policy));

        List<WatermarkPolicy> result = service.getExportPolicies();

        assertEquals(1, result.size());
    }

    @Test
    void updatePolicy_delegates() {
        WatermarkPolicy policy = new WatermarkPolicy();
        when(auditExportService.updatePolicy("p1", true, "tpl")).thenReturn(policy);

        WatermarkPolicy result = service.updatePolicy("p1", true, "tpl");

        assertSame(policy, result);
    }
}
