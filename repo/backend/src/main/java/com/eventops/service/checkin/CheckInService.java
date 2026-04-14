package com.eventops.service.checkin;

import com.eventops.audit.logging.AuditService;
import com.eventops.common.dto.ConflictType;
import com.eventops.common.dto.checkin.CheckInResponse;
import com.eventops.common.exception.BusinessException;
import com.eventops.domain.audit.AuditActionType;
import com.eventops.domain.checkin.CheckInRecord;
import com.eventops.domain.checkin.CheckInStatus;
import com.eventops.domain.checkin.DeviceBinding;
import com.eventops.domain.event.EventSession;
import com.eventops.domain.notification.NotificationType;
import com.eventops.repository.checkin.CheckInRecordRepository;
import com.eventops.repository.checkin.DeviceBindingRepository;
import com.eventops.repository.event.EventSessionRepository;
import com.eventops.repository.registration.RegistrationRepository;
import com.eventops.service.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class CheckInService {

    private static final Logger log = LoggerFactory.getLogger(CheckInService.class);
        private static final long CONCURRENT_DEVICE_WINDOW_SECONDS = 300;

    private final CheckInRecordRepository checkInRecordRepository;
    private final DeviceBindingRepository deviceBindingRepository;
    private final EventSessionRepository eventSessionRepository;
    private final RegistrationRepository registrationRepository;
    private final PasscodeService passcodeService;
    private final AuditService auditService;
        private final NotificationService notificationService;

    public CheckInService(CheckInRecordRepository checkInRecordRepository,
                          DeviceBindingRepository deviceBindingRepository,
                          EventSessionRepository eventSessionRepository,
                          RegistrationRepository registrationRepository,
                          PasscodeService passcodeService,
                          AuditService auditService,
                          NotificationService notificationService) {
        this.checkInRecordRepository = checkInRecordRepository;
        this.deviceBindingRepository = deviceBindingRepository;
        this.eventSessionRepository = eventSessionRepository;
        this.registrationRepository = registrationRepository;
        this.passcodeService = passcodeService;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    public CheckInResponse checkIn(String sessionId, String userId, String staffId,
                                    String passcode, String deviceToken) {
        EventSession session = eventSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException("Session not found", 404, "NOT_FOUND"));

        // Step 1: Enforce check-in window
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowOpen = session.getStartTime()
                .minusMinutes(session.getCheckinWindowBeforeMinutes());
        LocalDateTime windowClose = session.getStartTime()
                .plusMinutes(session.getCheckinWindowAfterMinutes());

        if (now.isBefore(windowOpen) || now.isAfter(windowClose)) {
            CheckInRecord denied = createRecord(sessionId, userId, staffId,
                    CheckInStatus.DENIED_WINDOW_CLOSED, passcode, null, null);
            checkInRecordRepository.save(denied);
            sendCheckInNotification(userId, NotificationType.CHECKIN_EXCEPTION, denied.getId(),
                    session.getTitle(), "WINDOW_CLOSED", denied.getStatus().name());
            auditService.logForCurrentUser(AuditActionType.CHECKIN_DENIED,
                    "CheckIn", denied.getId(),
                    "Check-in denied: window closed. Opens " + windowOpen + " closes " + windowClose);
            throw new BusinessException(
                    "Check-in window is not open. Opens at " + windowOpen + " and closes at " + windowClose,
                    422, "WINDOW_CLOSED", ConflictType.WINDOW_CLOSED);
        }

        // Step 2: Validate passcode
        if (!passcodeService.validatePasscode(sessionId, passcode)) {
            CheckInRecord denied = createRecord(sessionId, userId, staffId,
                    CheckInStatus.DENIED_INVALID_PASSCODE, passcode, null, null);
            checkInRecordRepository.save(denied);
            sendCheckInNotification(userId, NotificationType.CHECKIN_EXCEPTION, denied.getId(),
                    session.getTitle(), "INVALID_PASSCODE", denied.getStatus().name());
            auditService.logForCurrentUser(AuditActionType.CHECKIN_DENIED,
                    "CheckIn", denied.getId(), "Check-in denied: invalid passcode");
            throw new BusinessException("Invalid passcode", 422, "INVALID_PASSCODE");
        }

        // Step 3: Check user is registered
        boolean isRegistered = registrationRepository.existsByUserIdAndSessionId(userId, sessionId);
        if (!isRegistered) {
            CheckInRecord denied = createRecord(sessionId, userId, staffId,
                    CheckInStatus.DENIED_NOT_REGISTERED, passcode, null, null);
            checkInRecordRepository.save(denied);
            auditService.logForCurrentUser(AuditActionType.CHECKIN_DENIED,
                    "CheckIn", denied.getId(), "Check-in denied: not registered");
            throw new BusinessException("Attendee is not registered for this session",
                    422, "NOT_REGISTERED");
        }

        // Step 4: Check duplicate check-in
        if (checkInRecordRepository.existsBySessionIdAndUserIdAndStatus(
                sessionId, userId, CheckInStatus.CHECKED_IN)) {
            CheckInRecord denied = createRecord(sessionId, userId, staffId,
                    CheckInStatus.DENIED_DUPLICATE, passcode, null, null);
            checkInRecordRepository.save(denied);
            throw new BusinessException("Already checked in for this session",
                    409, "DUPLICATE_CHECKIN", ConflictType.DUPLICATE_CHECKIN);
        }

        // Step 5: Device binding enforcement
        String normalizedDeviceToken = normalizeDeviceToken(deviceToken);
        if (session.isDeviceBindingRequired() && normalizedDeviceToken == null) {
            throw new BusinessException(
                    "Device token is required when device binding is enabled",
                    422,
                    "DEVICE_TOKEN_REQUIRED"
            );
        }

        String tokenHash = null;
        if (normalizedDeviceToken != null) {
            tokenHash = hashDeviceToken(normalizedDeviceToken);

            if (session.isDeviceBindingRequired()) {
                Optional<DeviceBinding> existingBinding =
                        deviceBindingRepository.findByUserIdAndBindingDate(userId, LocalDate.now());

                if (existingBinding.isPresent()
                        && !existingBinding.get().getDeviceTokenHash().equals(tokenHash)) {
                    CheckInRecord denied = createRecord(sessionId, userId, staffId,
                            CheckInStatus.DENIED_DEVICE_CONFLICT, passcode, tokenHash, normalizedDeviceToken);
                    checkInRecordRepository.save(denied);
                    sendCheckInNotification(userId, NotificationType.CHECKIN_DEVICE_WARNING, denied.getId(),
                            session.getTitle(), "DEVICE_CONFLICT", denied.getStatus().name());
                    auditService.logForCurrentUser(AuditActionType.DEVICE_CONFLICT_WARNING,
                            "CheckIn", denied.getId(),
                            "Device conflict: already bound to different device today");
                    throw new BusinessException(
                            "Device conflict — already bound to a different device today",
                            409, "DEVICE_CONFLICT", ConflictType.DEVICE_CONFLICT);
                }

                if (existingBinding.isEmpty()) {
                    DeviceBinding binding = new DeviceBinding();
                    binding.setUserId(userId);
                    binding.setDeviceTokenHash(tokenHash);
                    binding.setDeviceTokenEncrypted(normalizedDeviceToken);
                    binding.setBindingDate(LocalDate.now());
                    deviceBindingRepository.save(binding);
                    auditService.logForCurrentUser(AuditActionType.DEVICE_BOUND,
                            "DeviceBinding", binding.getId(),
                            "Device bound for user on " + LocalDate.now());
                    log.info("Device bound: user={}, date={}", userId, LocalDate.now());
                }
            }
        }

        // Step 6: Create successful check-in record
        if (tokenHash != null) {
            Optional<CheckInRecord> recentOtherSessionCheckIn = checkInRecordRepository
                    .findTopByUserIdAndStatusAndSessionIdNotOrderByCheckedInAtDesc(
                            userId,
                            CheckInStatus.CHECKED_IN,
                            sessionId);

            if (recentOtherSessionCheckIn.isPresent()) {
                CheckInRecord prior = recentOtherSessionCheckIn.get();
                boolean withinConcurrentWindow = prior.getCheckedInAt() != null
                        && prior.getCheckedInAt().isAfter(Instant.now().minusSeconds(CONCURRENT_DEVICE_WINDOW_SECONDS));
                boolean differentDevice = prior.getDeviceTokenHash() != null
                        && !prior.getDeviceTokenHash().equals(tokenHash);

                if (withinConcurrentWindow && differentDevice) {
                    CheckInRecord conflict = createRecord(sessionId, userId, staffId,
                            CheckInStatus.CONFLICT_MULTI_DEVICE, passcode, tokenHash, normalizedDeviceToken);
                    checkInRecordRepository.save(conflict);
                    sendCheckInNotification(userId, NotificationType.CHECKIN_DEVICE_WARNING, conflict.getId(),
                            session.getTitle(), "CONCURRENT_DEVICE", conflict.getStatus().name());

                    auditService.logForCurrentUser(AuditActionType.DEVICE_CONFLICT_WARNING,
                            "CheckIn", conflict.getId(),
                            "Concurrent multi-device attempt detected for user");

                    throw new BusinessException(
                            "Concurrent multi-device attempt detected",
                            409,
                            "CONCURRENT_DEVICE",
                            ConflictType.CONCURRENT_DEVICE);
                }
            }
        }

        CheckInRecord record = createRecord(sessionId, userId, staffId,
                CheckInStatus.CHECKED_IN, passcode, tokenHash, normalizedDeviceToken);
        checkInRecordRepository.save(record);

        auditService.logForCurrentUser(AuditActionType.CHECKIN_SUCCESS,
                "CheckIn", record.getId(),
                "Successful check-in for session: " + session.getTitle());

        log.info("Check-in success: user={}, session={}", userId, sessionId);
        return mapToResponse(record, "Check-in successful");
    }

    @Transactional(readOnly = true)
    public List<CheckInResponse> getRoster(String sessionId) {
        return checkInRecordRepository.findBySessionId(sessionId).stream()
                .map(r -> mapToResponse(r, null))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CheckInResponse> getConflicts(String sessionId) {
        List<CheckInRecord> conflicts = checkInRecordRepository
                .findBySessionIdAndStatus(sessionId, CheckInStatus.DENIED_DEVICE_CONFLICT);
        List<CheckInRecord> multiDevice = checkInRecordRepository
                .findBySessionIdAndStatus(sessionId, CheckInStatus.CONFLICT_MULTI_DEVICE);
        conflicts.addAll(multiDevice);
        return conflicts.stream()
                .map(r -> mapToResponse(r, null))
                .collect(Collectors.toList());
    }

    private CheckInRecord createRecord(String sessionId, String userId, String staffId,
                                       CheckInStatus status, String passcode,
                                       String tokenHash, String deviceToken) {
        CheckInRecord record = new CheckInRecord();
        record.setSessionId(sessionId);
        record.setUserId(userId);
        record.setStaffId(staffId);
        record.setStatus(status);
        record.setPasscodeUsed(passcode);
        record.setDeviceTokenHash(tokenHash);
        record.setDeviceTokenEncrypted(deviceToken);
        record.setCheckedInAt(Instant.now());
        return record;
    }

    private CheckInResponse mapToResponse(CheckInRecord record, String message) {
        CheckInResponse response = new CheckInResponse();
        response.setId(record.getId());
        response.setSessionId(record.getSessionId());
        response.setUserId(record.getUserId());
        response.setStatus(record.getStatus().name());
        response.setCheckedInAt(record.getCheckedInAt());
        response.setMessage(message);
        return response;
    }

    private String hashDeviceToken(String deviceToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(deviceToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private String normalizeDeviceToken(String deviceToken) {
        if (deviceToken == null) {
            return null;
        }
        String normalized = deviceToken.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private void sendCheckInNotification(String userId,
                                         NotificationType type,
                                         String referenceId,
                                         String sessionTitle,
                                         String reason,
                                         String status) {
        Map<String, String> templateVars = new HashMap<>();
        templateVars.put("sessionTitle", sessionTitle);
        templateVars.put("reason", reason);
        templateVars.put("status", status);
        String safeReferenceId = (referenceId == null || referenceId.isBlank())
                ? UUID.randomUUID().toString()
                : referenceId;
        notificationService.sendNotification(userId, type, safeReferenceId, templateVars);
    }
}
