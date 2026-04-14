package com.eventops.domain.event;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * An event session (training, certification, community event) with seat capacity.
 */
@Entity
@Table(name = "event_sessions")
public class EventSession {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 100)
    private String location;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "max_capacity", nullable = false)
    private int maxCapacity;

    @Column(name = "current_registrations", nullable = false)
    private int currentRegistrations = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SessionStatus status = SessionStatus.DRAFT;

    @Column(name = "checkin_window_before_minutes", nullable = false)
    private int checkinWindowBeforeMinutes = 30;

    @Column(name = "checkin_window_after_minutes", nullable = false)
    private int checkinWindowAfterMinutes = 15;

    @Column(name = "device_binding_required", nullable = false)
    private boolean deviceBindingRequired = false;

    @Column(name = "waitlist_promotion_cutoff")
    private LocalDateTime waitlistPromotionCutoff;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    @Transient
    public int getRemainingSeats() {
        return Math.max(0, maxCapacity - currentRegistrations);
    }

    @Transient
    public boolean isFull() {
        return currentRegistrations >= maxCapacity;
    }

    // --- Getters and Setters ---
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
    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }
    public int getCheckinWindowBeforeMinutes() { return checkinWindowBeforeMinutes; }
    public void setCheckinWindowBeforeMinutes(int m) { this.checkinWindowBeforeMinutes = m; }
    public int getCheckinWindowAfterMinutes() { return checkinWindowAfterMinutes; }
    public void setCheckinWindowAfterMinutes(int m) { this.checkinWindowAfterMinutes = m; }
    public boolean isDeviceBindingRequired() { return deviceBindingRequired; }
    public void setDeviceBindingRequired(boolean d) { this.deviceBindingRequired = d; }
    public LocalDateTime getWaitlistPromotionCutoff() { return waitlistPromotionCutoff; }
    public void setWaitlistPromotionCutoff(LocalDateTime w) { this.waitlistPromotionCutoff = w; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
