package com.eventops.domain.notification;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalTime;

/**
 * Do-not-disturb window per user. Default 9:00 PM - 7:00 AM.
 */
@Entity
@Table(name = "dnd_rules")
public class DndRule {

    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime = LocalTime.of(21, 0);

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime = LocalTime.of(7, 0);

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() { updatedAt = Instant.now(); }

    @PreUpdate
    protected void onUpdate() { updatedAt = Instant.now(); }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getUpdatedAt() { return updatedAt; }
}
