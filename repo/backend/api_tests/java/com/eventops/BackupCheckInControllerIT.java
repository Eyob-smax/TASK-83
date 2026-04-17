package com.eventops;

import com.eventops.domain.backup.BackupJob;
import com.eventops.domain.backup.BackupStatus;
import com.eventops.domain.checkin.PasscodeState;
import com.eventops.domain.event.EventSession;
import com.eventops.domain.event.SessionStatus;
import com.eventops.domain.registration.Registration;
import com.eventops.domain.registration.RegistrationStatus;
import com.eventops.domain.user.RoleType;
import com.eventops.repository.backup.BackupJobRepository;
import com.eventops.repository.checkin.CheckInRecordRepository;
import com.eventops.repository.checkin.PasscodeStateRepository;
import com.eventops.repository.event.EventSessionRepository;
import com.eventops.repository.registration.RegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackupCheckInControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private BackupJobRepository backupJobRepository;
    @Autowired private EventSessionRepository eventSessionRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private PasscodeStateRepository passcodeStateRepository;
    @Autowired private CheckInRecordRepository checkInRecordRepository;

    @BeforeEach
    void resetData() {
        checkInRecordRepository.deleteAll();
        registrationRepository.deleteAll();
        passcodeStateRepository.deleteAll();
        eventSessionRepository.deleteAll();
        backupJobRepository.deleteAll();
    }

    @Test
    void listBackups_returnsJobs() throws Exception {
        seedBackupJob(BackupStatus.COMPLETED, Instant.now().plusSeconds(3600));

        mockMvc.perform(get("/api/admin/backups")
                        .with(TestSecurity.user("admin-1", "admin", RoleType.SYSTEM_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getBackup_returnsJob() throws Exception {
        BackupJob job = seedBackupJob(BackupStatus.COMPLETED, Instant.now().plusSeconds(3600));

        mockMvc.perform(get("/api/admin/backups/" + job.getId())
                        .with(TestSecurity.user("admin-1", "admin", RoleType.SYSTEM_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void triggerBackup_returnsCreatedBackup() throws Exception {
        mockMvc.perform(post("/api/admin/backups/trigger")
                        .with(csrf())
                        .with(TestSecurity.user("admin-1", "admin", RoleType.SYSTEM_ADMIN)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Backup triggered"));
    }

    @Test
    void getRetentionStatus_returnsAggregatedCounts() throws Exception {
        seedBackupJob(BackupStatus.COMPLETED, Instant.now().plusSeconds(3600));
        seedBackupJob(BackupStatus.COMPLETED, Instant.now().minusSeconds(3600));
        seedBackupJob(BackupStatus.FAILED, null);
        seedBackupJob(BackupStatus.IN_PROGRESS, null);

        mockMvc.perform(get("/api/admin/backups/retention")
                        .with(TestSecurity.user("admin-1", "admin", RoleType.SYSTEM_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalBackups").value(4))
                .andExpect(jsonPath("$.data.completedCount").value(2))
                .andExpect(jsonPath("$.data.failedCount").value(1))
                .andExpect(jsonPath("$.data.inProgressCount").value(1));
    }

    @Test
    void getPasscode_returnsCurrentPasscode() throws Exception {
        seedPasscode("session-1", "123456");

        mockMvc.perform(get("/api/checkin/sessions/session-1/passcode")
                        .with(TestSecurity.user("staff-1", "staff", RoleType.EVENT_STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.passcode").value("123456"));
    }

    @Test
    void checkIn_ineligibleStatus_returns422() throws Exception {
        EventSession session = createSession("checkin-session", LocalDateTime.now().plusMinutes(5));
        eventSessionRepository.save(session);

        Registration registration = new Registration();
        registration.setUserId("attendee-1");
        registration.setSessionId(session.getId());
        registration.setStatus(RegistrationStatus.WAITLISTED);
        registrationRepository.save(registration);

        seedPasscode(session.getId(), "222222");

        mockMvc.perform(post("/api/checkin/sessions/" + session.getId())
                        .with(csrf())
                        .with(TestSecurity.user("staff-1", "staff", RoleType.EVENT_STAFF))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"attendee-1\",\"passcode\":\"222222\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].code").value("INELIGIBLE_STATUS"));
    }

    @Test
    void getRoster_returnsRosterEntries() throws Exception {
        EventSession session = createSession("roster-session", LocalDateTime.now().plusMinutes(10));
        eventSessionRepository.save(session);

        Registration registration = new Registration();
        registration.setUserId("attendee-1");
        registration.setSessionId(session.getId());
        registration.setStatus(RegistrationStatus.CONFIRMED);
        registrationRepository.save(registration);

        mockMvc.perform(get("/api/checkin/sessions/" + session.getId() + "/roster")
                        .with(TestSecurity.user("staff-1", "staff", RoleType.EVENT_STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getConflicts_returnsConflictEntries() throws Exception {
        EventSession session = createSession("conflicts-session", LocalDateTime.now().plusMinutes(10));
        eventSessionRepository.save(session);

        mockMvc.perform(get("/api/checkin/sessions/" + session.getId() + "/conflicts")
                        .with(TestSecurity.user("staff-1", "staff", RoleType.EVENT_STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ---- seed helpers ----

    private BackupJob seedBackupJob(BackupStatus status, Instant retentionExpiresAt) {
        BackupJob job = new BackupJob();
        job.setStatus(status);
        job.setTriggeredBy("admin-1");
        job.setRetentionExpiresAt(retentionExpiresAt);
        return backupJobRepository.save(job);
    }

    private void seedPasscode(String sessionId, String passcode) {
        PasscodeState state = new PasscodeState();
        state.setSessionId(sessionId);
        state.setCurrentPasscode(passcode);
        state.setGeneratedAt(Instant.now());
        state.setExpiresAt(Instant.now().plusSeconds(120));
        passcodeStateRepository.save(state);
    }

    private EventSession createSession(String id, LocalDateTime startTime) {
        EventSession session = new EventSession();
        session.setId(id);
        session.setTitle("Session " + id);
        session.setDescription("Check-in integration test session");
        session.setLocation("Room B");
        session.setStartTime(startTime);
        session.setEndTime(startTime.plusHours(1));
        session.setMaxCapacity(10);
        session.setCurrentRegistrations(1);
        session.setStatus(SessionStatus.OPEN_FOR_REGISTRATION);
        session.setCheckinWindowBeforeMinutes(30);
        session.setCheckinWindowAfterMinutes(15);
        session.setDeviceBindingRequired(false);
        return session;
    }
}
