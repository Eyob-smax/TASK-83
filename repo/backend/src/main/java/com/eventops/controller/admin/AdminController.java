package com.eventops.controller.admin;

import com.eventops.common.dto.ApiResponse;
import com.eventops.common.dto.PagedResponse;
import com.eventops.common.dto.admin.AdminUserCreateRequest;
import com.eventops.common.dto.admin.AdminUserUpdateRequest;
import com.eventops.common.dto.admin.SecuritySettingsRequest;
import com.eventops.common.dto.admin.SecuritySettingsResponse;
import com.eventops.common.dto.auth.UserResponse;
import com.eventops.security.auth.EventOpsUserDetails;
import com.eventops.service.admin.AdminService;
import com.eventops.service.admin.SecuritySettingsService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final SecuritySettingsService securitySettingsService;

    public AdminController(AdminService adminService,
                           SecuritySettingsService securitySettingsService) {
        this.adminService = adminService;
        this.securitySettingsService = securitySettingsService;
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PagedResponse<UserResponse>>> listUsers(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(adminService.listUsers(pageable)));
    }

    @PostMapping("/users")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody AdminUserCreateRequest request,
            @AuthenticationPrincipal EventOpsUserDetails principal) {
        UserResponse response = adminService.createUser(
                request,
                principal.getUser().getId(),
                principal.getUsername()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "User created"));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable String id,
            @Valid @RequestBody AdminUserUpdateRequest request,
            @AuthenticationPrincipal EventOpsUserDetails principal) {
        UserResponse response = adminService.updateUser(
                id,
                request,
                principal.getUser().getId(),
                principal.getUsername()
        );
        return ResponseEntity.ok(ApiResponse.success(response, "User updated"));
    }

    @GetMapping("/security/settings")
    public ResponseEntity<ApiResponse<SecuritySettingsResponse>> getSecuritySettings() {
        return ResponseEntity.ok(ApiResponse.success(securitySettingsService.getSettingsResponse()));
    }

    @PutMapping("/security/settings")
    public ResponseEntity<ApiResponse<SecuritySettingsResponse>> updateSecuritySettings(
            @Valid @RequestBody SecuritySettingsRequest request,
            @AuthenticationPrincipal EventOpsUserDetails principal) {
        SecuritySettingsResponse response = securitySettingsService.updateSettings(
                request,
                principal.getUser().getId(),
                principal.getUsername()
        );
        return ResponseEntity.ok(ApiResponse.success(response, "Security settings updated"));
    }
}
