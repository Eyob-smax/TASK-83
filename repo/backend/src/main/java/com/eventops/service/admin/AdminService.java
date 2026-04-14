package com.eventops.service.admin;

import com.eventops.audit.logging.AuditService;
import com.eventops.audit.logging.FieldChange;
import com.eventops.common.dto.PagedResponse;
import com.eventops.common.dto.admin.AdminUserCreateRequest;
import com.eventops.common.dto.admin.AdminUserUpdateRequest;
import com.eventops.common.dto.auth.UserResponse;
import com.eventops.common.exception.BusinessException;
import com.eventops.domain.audit.AuditActionType;
import com.eventops.domain.user.AccountStatus;
import com.eventops.domain.user.User;
import com.eventops.repository.user.UserRepository;
import com.eventops.security.auth.PasswordService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class AdminService {

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final AuditService auditService;

    public AdminService(UserRepository userRepository,
                        PasswordService passwordService,
                        AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> listUsers(Pageable pageable) {
        Page<User> page = userRepository.findAll(pageable);
        return new PagedResponse<>(
                page.map(this::mapToResponse).getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }

    public UserResponse createUser(AdminUserCreateRequest request,
                                   String operatorId,
                                   String operatorName) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("Username is already taken", 409, "USERNAME_TAKEN");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordService.encode(request.getPassword()));
        user.setDisplayName(request.getDisplayName());
        user.setRoleType(request.getRoleType());
        user.setStatus(request.getStatus() == null ? AccountStatus.ACTIVE : request.getStatus());
        user.setContactInfo(request.getContactInfo());

        User saved = userRepository.save(user);
        auditService.log(
                AuditActionType.ACCOUNT_CREATED,
                operatorId,
                operatorName,
                "WEB",
                "User",
                saved.getId(),
                "Administrator created user " + saved.getUsername()
        );

        return mapToResponse(saved);
    }

    public UserResponse updateUser(String userId,
                                   AdminUserUpdateRequest request,
                                   String operatorId,
                                   String operatorName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found", 404, "NOT_FOUND"));

        List<FieldChange> changes = new ArrayList<>();

        if (request.getDisplayName() != null && !request.getDisplayName().equals(user.getDisplayName())) {
            changes.add(new FieldChange("displayName", user.getDisplayName(), request.getDisplayName()));
            user.setDisplayName(request.getDisplayName());
        }

        if (request.getRoleType() != null && request.getRoleType() != user.getRoleType()) {
            changes.add(new FieldChange("roleType", user.getRoleType().name(), request.getRoleType().name()));
            user.setRoleType(request.getRoleType());
        }

        if (request.getStatus() != null && request.getStatus() != user.getStatus()) {
            changes.add(new FieldChange("status", user.getStatus().name(), request.getStatus().name()));
            user.setStatus(request.getStatus());
        }

        if (request.getContactInfo() != null && !request.getContactInfo().equals(user.getContactInfo())) {
            changes.add(new FieldChange("contactInfo", user.getContactInfo(), request.getContactInfo()));
            user.setContactInfo(request.getContactInfo());
        }

        User saved = userRepository.save(user);
        if (!changes.isEmpty()) {
            auditService.logWithDiffs(
                    AuditActionType.USER_ROLE_CHANGED,
                    operatorId,
                    operatorName,
                    "WEB",
                    "User",
                    saved.getId(),
                    "Administrator updated user " + saved.getUsername(),
                    changes
            );
        }

        return mapToResponse(saved);
    }

    private UserResponse mapToResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setDisplayName(user.getDisplayName());
        response.setContactInfoMasked(user.getContactInfo() == null || user.getContactInfo().isBlank() ? null : "****");
        response.setRoleType(user.getRoleType().name());
        response.setStatus(user.getStatus().name());
        response.setLastLoginAt(user.getLastLoginAt());
        response.setCreatedAt(user.getCreatedAt());
        return response;
    }
}
