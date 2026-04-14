package com.eventops.domain.checkin;

import com.eventops.security.encryption.EncryptedFieldConverter;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * Records each check-in attempt with outcome and device context.
 */
@Entity
@Table(name = "checkin_records")
public class CheckInRecord {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "staff_id", nullable = false, length = 36)
    private String staffId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CheckInStatus status;

    @Column(name = "passcode_used", length = 6)
    private String passcodeUsed;

    @Column(name = "device_token_hash", length = 128)
    private String deviceTokenHash;

    @Convert(converter = EncryptedFieldConverter.class)
    @Column(name = "device_token_encrypted", columnDefinition = "VARBINARY(512)")
    private String deviceTokenEncrypted;

    @Column(name = "device_fingerprint", columnDefinition = "VARBINARY(512)")
    private byte[] deviceFingerprint; // encrypted at rest

    @Column(name = "request_source", length = 50)
    private String requestSource;

    @Column(name = "checked_in_at", nullable = false)
    private Instant checkedInAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        createdAt = Instant.now();
        if (checkedInAt == null) checkedInAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getStaffId() { return staffId; }
    public void setStaffId(String staffId) { this.staffId = staffId; }
    public CheckInStatus getStatus() { return status; }
    public void setStatus(CheckInStatus status) { this.status = status; }
    public String getPasscodeUsed() { return passcodeUsed; }
    public void setPasscodeUsed(String passcodeUsed) { this.passcodeUsed = passcodeUsed; }
    public String getDeviceTokenHash() { return deviceTokenHash; }
    public void setDeviceTokenHash(String deviceTokenHash) { this.deviceTokenHash = deviceTokenHash; }
    public String getDeviceTokenEncrypted() { return deviceTokenEncrypted; }
    public void setDeviceTokenEncrypted(String deviceTokenEncrypted) { this.deviceTokenEncrypted = deviceTokenEncrypted; }
    public byte[] getDeviceFingerprint() { return deviceFingerprint; }
    public void setDeviceFingerprint(byte[] deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }
    public String getRequestSource() { return requestSource; }
    public void setRequestSource(String requestSource) { this.requestSource = requestSource; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public void setCheckedInAt(Instant checkedInAt) { this.checkedInAt = checkedInAt; }
    public Instant getCreatedAt() { return createdAt; }
}
