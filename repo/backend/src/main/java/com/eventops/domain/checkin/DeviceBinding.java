package com.eventops.domain.checkin;

import com.eventops.security.encryption.EncryptedFieldConverter;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One-time device binding record per user per day.
 */
@Entity
@Table(name = "device_bindings", uniqueConstraints = {
    @UniqueConstraint(name = "uk_device_binding_user_date", columnNames = {"user_id", "binding_date"})
})
public class DeviceBinding {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "device_token_hash", nullable = false, length = 128)
    private String deviceTokenHash;

    @Convert(converter = EncryptedFieldConverter.class)
    @Column(name = "device_token_encrypted", columnDefinition = "VARBINARY(512)")
    private String deviceTokenEncrypted;

    @Column(name = "device_fingerprint", columnDefinition = "VARBINARY(512)")
    private byte[] deviceFingerprint; // encrypted at rest

    @Column(name = "binding_date", nullable = false)
    private LocalDate bindingDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getDeviceTokenHash() { return deviceTokenHash; }
    public void setDeviceTokenHash(String deviceTokenHash) { this.deviceTokenHash = deviceTokenHash; }
    public String getDeviceTokenEncrypted() { return deviceTokenEncrypted; }
    public void setDeviceTokenEncrypted(String deviceTokenEncrypted) { this.deviceTokenEncrypted = deviceTokenEncrypted; }
    public byte[] getDeviceFingerprint() { return deviceFingerprint; }
    public void setDeviceFingerprint(byte[] deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }
    public LocalDate getBindingDate() { return bindingDate; }
    public void setBindingDate(LocalDate bindingDate) { this.bindingDate = bindingDate; }
    public Instant getCreatedAt() { return createdAt; }
}
