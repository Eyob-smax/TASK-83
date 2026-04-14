package com.eventops.domain.checkin;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Tracks current rotating passcode state per session.
 */
@Entity
@Table(name = "passcode_states")
public class PasscodeState {

    @Id
    @Column(name = "session_id", length = 36)
    private String sessionId;

    @Column(name = "current_passcode", nullable = false, length = 6)
    private String currentPasscode;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getCurrentPasscode() { return currentPasscode; }
    public void setCurrentPasscode(String currentPasscode) { this.currentPasscode = currentPasscode; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
