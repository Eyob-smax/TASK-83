package com.eventops.common.dto.export;

import jakarta.validation.constraints.NotBlank;

public class RosterExportRequest {

    @NotBlank
    private String sessionId;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
