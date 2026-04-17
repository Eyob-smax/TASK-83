package com.eventops;

import com.eventops.domain.audit.ExportJob;
import com.eventops.domain.audit.ExportStatus;
import com.eventops.domain.audit.WatermarkPolicy;
import com.eventops.domain.importing.CrawlJob;
import com.eventops.domain.importing.ImportJobStatus;
import com.eventops.domain.importing.ImportMode;
import com.eventops.domain.importing.ImportSource;
import com.eventops.domain.user.AccountStatus;
import com.eventops.domain.user.RoleType;
import com.eventops.domain.user.User;
import com.eventops.repository.audit.ExportJobRepository;
import com.eventops.repository.audit.WatermarkPolicyRepository;
import com.eventops.repository.importing.CrawlJobRepository;
import com.eventops.repository.importing.ImportSourceRepository;
import com.eventops.repository.user.UserRepository;
import com.eventops.security.auth.PasswordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImportExportControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ImportSourceRepository importSourceRepository;
    @Autowired private CrawlJobRepository crawlJobRepository;
    @Autowired private ExportJobRepository exportJobRepository;
    @Autowired private WatermarkPolicyRepository watermarkPolicyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordService passwordService;

    @BeforeEach
    void resetData() {
        crawlJobRepository.deleteAll();
        importSourceRepository.deleteAll();
        exportJobRepository.deleteAll();
        watermarkPolicyRepository.deleteAll();
    }

    // ---- Import Tests ----

    @Test
    void getSources_returnsImportSources() throws Exception {
        seedImportSource("source-1");

        mockMvc.perform(get("/api/imports/sources")
                        .with(TestSecurity.user("admin-1", "admin", RoleType.SYSTEM_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createSource_returnsCreatedSource() throws Exception {
        mockMvc.perform(post("/api/imports/sources")
                        .with(csrf())
                        .with(TestSecurity.user("admin-1", "admin", RoleType.SYSTEM_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "North Folder",
                                  "folderPath": "north",
                                  "filePattern": "*.csv",
                                  "importMode": "INCREMENTAL",
                                  "columnMappings": "{}",
                                  "concurrencyCap": 2,
                                  "timeoutSeconds": 60,
                                  "circuitBreakerThreshold": 3,
                                  "active": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Import source created"));
    }

    @Test
    void updateSource_returnsUpdatedSource() throws Exception {
        ImportSource source = seedImportSource("source-1");

        mockMvc.perform(put("/api/imports/sources/" + source.getId())
                        .with(csrf())
                        .with(TestSecurity.user("admin-1", "admin", RoleType.SYSTEM_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "North Folder",
                                  "folderPath": "north",
                                  "filePattern": "*.csv",
                                  "importMode": "FULL",
                                  "columnMappings": "{}",
                                  "concurrencyCap": 2,
                                  "timeoutSeconds": 60,
                                  "circuitBreakerThreshold": 3,
                                  "active": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Import source updated"));
    }

    @Test
    void getJobs_returnsPagedJobs() throws Exception {
        ImportSource source = seedImportSource("source-1");
        seedCrawlJob(source.getId());

        mockMvc.perform(get("/api/imports/jobs")
                        .param("sourceId", source.getId())
                        .param("page", "0")
                        .param("size", "20")
                        .with(TestSecurity.user("admin-1", "admin", RoleType.SYSTEM_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void getJob_returnsSingleJob() throws Exception {
        ImportSource source = seedImportSource("source-1");
        CrawlJob job = seedCrawlJob(source.getId());

        mockMvc.perform(get("/api/imports/jobs/" + job.getId())
                        .with(TestSecurity.user("admin-1", "admin", RoleType.SYSTEM_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void triggerImport_returnsCreatedJob() throws Exception {
        ImportSource source = seedImportSource("source-1");

        mockMvc.perform(post("/api/imports/jobs/trigger")
                        .with(csrf())
                        .with(TestSecurity.user("admin-1", "admin", RoleType.SYSTEM_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceId": "%s",
                                  "mode": "INCREMENTAL",
                                  "priority": 5
                                }
                                """.formatted(source.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Import job triggered"));
    }

    @Test
    void getCircuitBreakerStatus_returnsStatusMap() throws Exception {
        seedImportSource("source-1");

        mockMvc.perform(get("/api/imports/circuit-breaker")
                        .with(TestSecurity.user("admin-1", "admin", RoleType.SYSTEM_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ---- Export Tests ----

    @Test
    void generateRosterExport_returnsCreatedJob() throws Exception {
        // Seed staff user for operator name resolution
        seedUser("staff-1", "staff", RoleType.EVENT_STAFF);

        mockMvc.perform(post("/api/exports/rosters")
                        .with(csrf())
                        .with(TestSecurity.user("staff-1", "staff", RoleType.EVENT_STAFF))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"session-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Roster export initiated"));
    }

    @Test
    void generateFinanceReport_returnsCreatedJob() throws Exception {
        // Seed finance user for operator name resolution
        seedUser("finance-1", "finance", RoleType.FINANCE_MANAGER);

        mockMvc.perform(post("/api/exports/finance-reports")
                        .with(csrf())
                        .with(TestSecurity.user("finance-1", "finance", RoleType.FINANCE_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodId\":\"period-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Finance report export initiated"));
    }

    @Test
    void getExportPolicies_returnsPolicies() throws Exception {
        seedWatermarkPolicy("policy-1");

        mockMvc.perform(get("/api/exports/policies")
                        .with(TestSecurity.user("admin-1", "admin", RoleType.SYSTEM_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updatePolicy_returnsUpdatedPolicy() throws Exception {
        WatermarkPolicy policy = seedWatermarkPolicy("policy-1");

        mockMvc.perform(put("/api/exports/policies")
                        .with(csrf())
                        .with(TestSecurity.user("admin-1", "admin", RoleType.SYSTEM_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "%s",
                                  "downloadAllowed": true,
                                  "watermarkTemplate": "Internal Use"
                                }
                                """.formatted(policy.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Export policy updated"));
    }

    // ---- seed helpers ----

    private ImportSource seedImportSource(String id) {
        ImportSource source = new ImportSource();
        source.setId(id);
        source.setName("Source " + id);
        source.setFolderPath(id);
        source.setFilePattern("*.csv");
        source.setImportMode(ImportMode.INCREMENTAL);
        source.setColumnMappings("{}");
        source.setConcurrencyCap(2);
        source.setTimeoutSeconds(60);
        source.setCircuitBreakerThreshold(3);
        source.setActive(true);
        return importSourceRepository.save(source);
    }

    private CrawlJob seedCrawlJob(String sourceId) {
        CrawlJob job = new CrawlJob();
        job.setSourceId(sourceId);
        job.setImportMode(ImportMode.INCREMENTAL);
        job.setStatus(ImportJobStatus.QUEUED);
        job.setPriority(100);
        return crawlJobRepository.save(job);
    }

    private WatermarkPolicy seedWatermarkPolicy(String id) {
        WatermarkPolicy policy = new WatermarkPolicy();
        policy.setId(id);
        policy.setReportType("ROSTER");
        policy.setRoleType(RoleType.SYSTEM_ADMIN.name());
        policy.setDownloadAllowed(true);
        policy.setWatermarkTemplate("Internal Use");
        policy.setActive(true);
        return watermarkPolicyRepository.save(policy);
    }

    private void seedUser(String id, String username, RoleType role) {
        if (userRepository.findById(id).isEmpty()) {
            User user = new User();
            user.setId(id);
            user.setUsername(username);
            user.setPasswordHash(passwordService.encode("password123"));
            user.setDisplayName("Display " + username);
            user.setRoleType(role);
            user.setStatus(AccountStatus.ACTIVE);
            userRepository.save(user);
        }
    }
}
