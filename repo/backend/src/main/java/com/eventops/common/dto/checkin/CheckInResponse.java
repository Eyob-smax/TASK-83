package com.eventops.common.dto.checkin;

import java.time.Instant;

public class CheckInResponse {
    private String id;
    private String sessionId;
    private String userId;
    private String status;
    private Instant checkedInAt;
    private String message;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public void setCheckedInAt(Instant checkedInAt) { this.checkedInAt = checkedInAt; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
