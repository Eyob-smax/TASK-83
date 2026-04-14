package com.eventops.common.dto.admin;

import com.eventops.domain.user.AccountStatus;
import com.eventops.domain.user.RoleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AdminUserCreateRequest {

    @NotBlank
    @Size(min = 3, max = 100, message = "Username must be 3-100 characters")
    private String username;

    @NotBlank
    @Size(min = 8, max = 128, message = "Password must be 8-128 characters")
    private String password;

    @NotBlank
    @Size(max = 200)
    private String displayName;

    @NotNull
    private RoleType roleType;

    private AccountStatus status = AccountStatus.ACTIVE;

    @Size(max = 255)
    private String contactInfo;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public RoleType getRoleType() {
        return roleType;
    }

    public void setRoleType(RoleType roleType) {
        this.roleType = roleType;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public String getContactInfo() {
        return contactInfo;
    }

    public void setContactInfo(String contactInfo) {
        this.contactInfo = contactInfo;
    }
}
