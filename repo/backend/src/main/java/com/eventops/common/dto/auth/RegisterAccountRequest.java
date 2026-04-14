package com.eventops.common.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterAccountRequest {
    @NotBlank @Size(min = 3, max = 100, message = "Username must be 3-100 characters")
    private String username;

    @NotBlank @Size(min = 8, max = 128, message = "Password must be 8-128 characters")
    private String password;

    @NotBlank @Size(max = 200)
    private String displayName;

    private String contactInfo;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getContactInfo() { return contactInfo; }
    public void setContactInfo(String contactInfo) { this.contactInfo = contactInfo; }
}
