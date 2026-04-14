package com.eventops.common.dto.event;

import java.time.LocalDateTime;
import java.time.Instant;

public class EventSessionResponse {
    private String id;
    private String title;
    private String description;
    private String location;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int maxCapacity;
    private int currentRegistrations;
    private int remainingSeats;
    private String status;
    private boolean deviceBindingRequired;
    private LocalDateTime waitlistPromotionCutoff;
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public int getMaxCapacity() { return maxCapacity; }
    public void setMaxCapacity(int maxCapacity) { this.maxCapacity = maxCapacity; }
    public int getCurrentRegistrations() { return currentRegistrations; }
    public void setCurrentRegistrations(int currentRegistrations) { this.currentRegistrations = currentRegistrations; }
    public int getRemainingSeats() { return remainingSeats; }
    public void setRemainingSeats(int remainingSeats) { this.remainingSeats = remainingSeats; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isDeviceBindingRequired() { return deviceBindingRequired; }
    public void setDeviceBindingRequired(boolean deviceBindingRequired) { this.deviceBindingRequired = deviceBindingRequired; }
    public LocalDateTime getWaitlistPromotionCutoff() { return waitlistPromotionCutoff; }
    public void setWaitlistPromotionCutoff(LocalDateTime waitlistPromotionCutoff) { this.waitlistPromotionCutoff = waitlistPromotionCutoff; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
