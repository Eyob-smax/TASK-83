package com.eventops;

import com.eventops.domain.audit.AuditActionType;
import com.eventops.domain.audit.AuditEvent;
import com.eventops.domain.audit.FieldDiff;
import com.eventops.domain.user.AccountStatus;
import com.eventops.domain.user.RoleType;
import com.eventops.domain.user.User;
import com.eventops.repository.audit.AuditEventRepository;
import com.eventops.repository.audit.ExportJobRepository;
import com.eventops.repository.audit.FieldDiffRepository;
import com.eventops.repository.user.UserRepository;
import com.eventops.security.auth.PasswordService;
import com.eventops.domain.audit.ExportJob;
import com.eventops.domain.audit.ExportStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private FieldDiffRepository fieldDiffRepository;
    @Autowired private ExportJobRepository exportJobRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordService passwordService;

    @BeforeEach
    void resetData() {
        exportJobRepository.deleteAll();
        fieldDiffRepository.deleteAll();
        auditEventRepository.deleteAll();
    }

    @Test
    void searchLogs_asSystemAdmin_returnsPagedAuditEvents() throws Exception {
        AuditEvent event = seedAuditEvent("audit-1", AuditActionType.USER_ROLE_CHANGED);

        mockMvc.perform(get("/api/audit/logs")
                        .param("actionType", "USER_ROLE_CHANGED")
                        .param("page", "0")
                        .param("size", "20")
                        .with(TestSecurity.user("admin-1", "admin", RoleType.SYSTEM_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(event.getId()))
                .andExpect(jsonPath("$.data.content[0].actionType").value("USER_ROLE_CHANGED"));
    }

    @Test
    void getLog_asSystemAdmin_returnsAuditLogDetails() throws Exception {
        AuditEvent event = seedAuditEvent("audit-2", AuditActionType.SECURITY_SETTING_CHANGED);
        seedFieldDiff(event.getId(), "signatureEnabled", "false", "true");

        mockMvc.perform(get("/api/audit/logs/" + event.getId())
                        .with(TestSecurity.user("admin-1", "admin", RoleType.SYSTEM_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(event.getId()))
                .andExpect(jsonPath("$.data.actionType").value("SECURITY_SETTING_CHANGED"))
                .andExpect(jsonPath("$.data.fieldDiffs[0].fieldName").value("signatureEnabled"))
                .andExpect(jsonPath("$.data.fieldDiffs[0].oldValue").value("false"))
                .andExpect(jsonPath("$.data.fieldDiffs[0].newValue").value("true"));
    }

    @Test
    void exportLogs_asSystemAdmin_returnsCreatedExportJob() throws Exception {
        // Seed a real admin user in the DB so the export service can resolve operator name
        seedAdminUser("admin-1", "admin");

        mockMvc.perform(post("/api/audit/logs/export")
                        .with(csrf())
                        .with(TestSecurity.user("admin-1", "admin", RoleType.SYSTEM_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "entityType": "USER",
                                  "operatorId": "admin-1"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Audit log export initiated"))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void searchLogs_asNonAdmin_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/audit/logs")
                        .with(TestSecurity.user("attendee-1", "attendee", RoleType.ATTENDEE)))
                .andExpect(status().isForbidden());
    }

    @TempDir
    Path tempDir;

    @Test
    void downloadExport_asSystemAdmin_returnsFileBytes() throws Exception {
        // Create a temp CSV file to serve as the export artifact
        Path exportFile = tempDir.resolve("audit_export.csv");
        Files.writeString(exportFile, "timestamp,action,operator\n2026-04-16,LOGIN,admin\n");

        // Seed admin user (needed for role check in download service)
        seedAdminUser("admin-1", "admin");

        // Seed a completed export job pointing to the temp file
        ExportJob job = new ExportJob();
        job.setId("export-dl-1");
        job.setExportType("AUDIT_LOG");
        job.setStatus(ExportStatus.COMPLETED);
        job.setRequestedBy("admin-1");
        job.setFilePath(exportFile.toAbsolutePath().toString());
        job.setFileSizeBytes(Files.size(exportFile));
        job.setCompletedAt(Instant.now());
        exportJobRepository.save(job);

        mockMvc.perform(get("/api/audit/exports/export-dl-1/download")
                        .with(TestSecurity.user("admin-1", "admin", RoleType.SYSTEM_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")));
    }

    // ---- seed helpers ----

    private AuditEvent seedAuditEvent(String id, AuditActionType actionType) {
        AuditEvent event = new AuditEvent();
        event.setId(id);
        event.setActionType(actionType);
        event.setOperatorId("admin-1");
        event.setOperatorName("Admin User");
        event.setRequestSource("WEB");
        event.setEntityType("USER");
        event.setEntityId("user-9");
        event.setDescription("Updated user access");
        return auditEventRepository.save(event);
    }

    private void seedFieldDiff(String auditEventId, String fieldName, String oldValue, String newValue) {
        FieldDiff diff = new FieldDiff();
        diff.setAuditEventId(auditEventId);
        diff.setFieldName(fieldName);
        diff.setOldValue(oldValue);
        diff.setNewValue(newValue);
        fieldDiffRepository.save(diff);
    }

    private void seedAdminUser(String id, String username) {
        if (userRepository.findById(id).isEmpty()) {
            User user = new User();
            user.setId(id);
            user.setUsername(username);
            user.setPasswordHash(passwordService.encode("password123"));
            user.setDisplayName("Admin User");
            user.setRoleType(RoleType.SYSTEM_ADMIN);
            user.setStatus(AccountStatus.ACTIVE);
            userRepository.save(user);
        }
    }
}
