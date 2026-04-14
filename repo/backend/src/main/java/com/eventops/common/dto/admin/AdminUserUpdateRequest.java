package com.eventops.common.dto.admin;

import com.eventops.domain.user.AccountStatus;
import com.eventops.domain.user.RoleType;
import jakarta.validation.constraints.Size;

public class AdminUserUpdateRequest {

    @Size(max = 200)
    private String displayName;

    private RoleType roleType;

    private AccountStatus status;

    @Size(max = 255)
    private String contactInfo;

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
