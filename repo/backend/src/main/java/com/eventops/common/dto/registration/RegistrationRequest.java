package com.eventops.common.dto.registration;

import jakarta.validation.constraints.NotBlank;

public class RegistrationRequest {
    @NotBlank(message = "Session ID is required")
    private String sessionId;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
