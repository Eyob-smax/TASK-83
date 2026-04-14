package com.eventops.common.dto.auth;

import java.time.Instant;

public class UserResponse {
    private String id;
    private String username;
    private String displayName;
    private String contactInfoMasked;
    private String roleType;
    private String status;
    private Instant lastLoginAt;
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getContactInfoMasked() { return contactInfoMasked; }
    public void setContactInfoMasked(String contactInfoMasked) { this.contactInfoMasked = contactInfoMasked; }
    public String getRoleType() { return roleType; }
    public void setRoleType(String roleType) { this.roleType = roleType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
