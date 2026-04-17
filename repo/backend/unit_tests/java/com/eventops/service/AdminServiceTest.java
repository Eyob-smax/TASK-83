package com.eventops.service;

import com.eventops.audit.logging.AuditService;
import com.eventops.audit.logging.FieldChange;
import com.eventops.common.dto.PagedResponse;
import com.eventops.common.dto.admin.AdminUserCreateRequest;
import com.eventops.common.dto.admin.AdminUserUpdateRequest;
import com.eventops.common.dto.auth.UserResponse;
import com.eventops.common.exception.BusinessException;
import com.eventops.domain.user.AccountStatus;
import com.eventops.domain.user.RoleType;
import com.eventops.domain.user.User;
import com.eventops.repository.user.UserRepository;
import com.eventops.security.auth.PasswordService;
import com.eventops.service.admin.AdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordService passwordService;
    @Mock AuditService auditService;
    @InjectMocks AdminService service;

    private User sampleUser(String id, String username, RoleType role) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setDisplayName("Display " + id);
        u.setPasswordHash("hash");
        u.setRoleType(role);
        u.setStatus(AccountStatus.ACTIVE);
        return u;
    }

    @Test
    void listUsers_returnsPagedResponse() {
        User u1 = sampleUser("u1", "alice", RoleType.ATTENDEE);
        User u2 = sampleUser("u2", "bob", RoleType.EVENT_STAFF);
        Pageable pageable = PageRequest.of(0, 20);
        Page<User> page = new PageImpl<>(List.of(u1, u2), pageable, 2);
        when(userRepository.findAll(pageable)).thenReturn(page);

        PagedResponse<UserResponse> result = service.listUsers(pageable);

        assertEquals(2, result.getContent().size());
        assertEquals("alice", result.getContent().get(0).getUsername());
        assertEquals(2L, result.getTotalElements());
    }

    @Test
    void listUsers_emptyPage() {
        Pageable pageable = PageRequest.of(0, 20);
        when(userRepository.findAll(pageable)).thenReturn(Page.empty(pageable));

        PagedResponse<UserResponse> result = service.listUsers(pageable);

        assertTrue(result.getContent().isEmpty());
        assertEquals(0L, result.getTotalElements());
    }

    @Test
    void createUser_happyPath_persistsAndLogsAudit() {
        AdminUserCreateRequest req = new AdminUserCreateRequest();
        req.setUsername("newuser");
        req.setPassword("password123");
        req.setDisplayName("New User");
        req.setRoleType(RoleType.ATTENDEE);

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(passwordService.encode("password123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("u-new");
            return u;
        });

        UserResponse resp = service.createUser(req, "admin-1", "Admin One");

        assertEquals("newuser", resp.getUsername());
        assertEquals("ATTENDEE", resp.getRoleType());
        verify(auditService).log(any(), eq("admin-1"), eq("Admin One"), eq("WEB"),
                eq("User"), anyString(), contains("created user newuser"));
    }

    @Test
    void createUser_usernameAlreadyTaken_throws409() {
        AdminUserCreateRequest req = new AdminUserCreateRequest();
        req.setUsername("taken");
        req.setPassword("password123");
        req.setDisplayName("X");
        req.setRoleType(RoleType.ATTENDEE);

        when(userRepository.existsByUsername("taken")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createUser(req, "admin-1", "Admin"));
        assertEquals(409, ex.getHttpStatus());
        assertEquals("USERNAME_TAKEN", ex.getErrorCode());
        verifyNoInteractions(auditService);
    }

    @Test
    void createUser_defaultStatusIsActive_whenNotProvided() {
        AdminUserCreateRequest req = new AdminUserCreateRequest();
        req.setUsername("u");
        req.setPassword("password123");
        req.setDisplayName("X");
        req.setRoleType(RoleType.ATTENDEE);
        req.setStatus(null);

        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordService.encode(anyString())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("u-x");
            return u;
        });

        service.createUser(req, "admin", "Admin");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(AccountStatus.ACTIVE, captor.getValue().getStatus());
    }

    @Test
    void updateUser_userNotFound_throws404() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        AdminUserUpdateRequest req = new AdminUserUpdateRequest();
        req.setDisplayName("New Name");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateUser("missing", req, "admin", "Admin"));
        assertEquals(404, ex.getHttpStatus());
    }

    @Test
    void updateUser_withDisplayNameChange_recordsAuditWithDiffs() {
        User existing = sampleUser("u1", "alice", RoleType.ATTENDEE);
        existing.setDisplayName("Old Name");
        when(userRepository.findById("u1")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminUserUpdateRequest req = new AdminUserUpdateRequest();
        req.setDisplayName("New Name");

        service.updateUser("u1", req, "admin", "Admin");

        verify(auditService).logWithDiffs(any(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyList());
    }

    @Test
    void updateUser_whenNoFieldsChange_doesNotAuditDiffs() {
        User existing = sampleUser("u1", "alice", RoleType.ATTENDEE);
        when(userRepository.findById("u1")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminUserUpdateRequest req = new AdminUserUpdateRequest();
        // All fields null — nothing changes

        service.updateUser("u1", req, "admin", "Admin");

        verify(auditService, never()).logWithDiffs(any(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyList());
    }

    @Test
    void updateUser_roleChangeCapturesDiff() {
        User existing = sampleUser("u1", "alice", RoleType.ATTENDEE);
        when(userRepository.findById("u1")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminUserUpdateRequest req = new AdminUserUpdateRequest();
        req.setRoleType(RoleType.EVENT_STAFF);

        service.updateUser("u1", req, "admin", "Admin");

        ArgumentCaptor<List<FieldChange>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditService).logWithDiffs(any(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals("roleType", captor.getValue().get(0).fieldName());
    }

    @Test
    void mapToResponse_masksContactInfoWhenPresent() {
        User u = sampleUser("u1", "alice", RoleType.ATTENDEE);
        u.setContactInfo("alice@example.com");
        when(userRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(u)));

        PagedResponse<UserResponse> result = service.listUsers(PageRequest.of(0, 10));

        assertEquals("****", result.getContent().get(0).getContactInfoMasked());
    }

    @Test
    void mapToResponse_nullContactInfo_producesNullMask() {
        User u = sampleUser("u1", "alice", RoleType.ATTENDEE);
        u.setContactInfo(null);
        when(userRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(u)));

        PagedResponse<UserResponse> result = service.listUsers(PageRequest.of(0, 10));

        assertNull(result.getContent().get(0).getContactInfoMasked());
    }
}
