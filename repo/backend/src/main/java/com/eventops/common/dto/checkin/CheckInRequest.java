package com.eventops.common.dto.checkin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class CheckInRequest {
    @NotBlank(message = "Attendee user ID is required")
    private String userId;

    @NotBlank(message = "Passcode is required")
    @Pattern(regexp = "\\d{6}", message = "Passcode must be exactly 6 digits")
    private String passcode;

    private String deviceToken;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getPasscode() { return passcode; }
    public void setPasscode(String passcode) { this.passcode = passcode; }
    public String getDeviceToken() { return deviceToken; }
    public void setDeviceToken(String deviceToken) { this.deviceToken = deviceToken; }
}
