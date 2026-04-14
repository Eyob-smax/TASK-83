package com.eventops.common.dto.notification;

import jakarta.validation.constraints.NotNull;

public class DndSettingsRequest {
    @NotNull private String startTime; // HH:mm format
    @NotNull private String endTime;   // HH:mm format
    private boolean enabled = true;

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
