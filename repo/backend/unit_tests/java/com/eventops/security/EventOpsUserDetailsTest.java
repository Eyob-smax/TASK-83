package com.eventops.security;

import com.eventops.domain.user.AccountStatus;
import com.eventops.domain.user.RoleType;
import com.eventops.domain.user.User;
import com.eventops.security.auth.EventOpsUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventOpsUserDetailsTest {

    private static User buildUser(AccountStatus status, RoleType role, Instant lockoutUntil) {
        User u = new User();
        u.setId("u-1");
        u.setUsername("alice");
        u.setPasswordHash("hash-value");
        u.setDisplayName("Alice");
        u.setStatus(status);
        u.setRoleType(role);
        u.setLockoutUntil(lockoutUntil);
        return u;
    }

    @Test
    void authorities_haveRolePrefix() {
        User u = buildUser(AccountStatus.ACTIVE, RoleType.ATTENDEE, null);
        EventOpsUserDetails details = new EventOpsUserDetails(u);
        List<GrantedAuthority> auths = List.copyOf(details.getAuthorities());
        assertEquals(1, auths.size());
        assertEquals("ROLE_ATTENDEE", auths.get(0).getAuthority());
    }

    @Test
    void authorities_reflectAssignedRole() {
        User u = buildUser(AccountStatus.ACTIVE, RoleType.FINANCE_MANAGER, null);
        EventOpsUserDetails details = new EventOpsUserDetails(u);
        assertEquals("ROLE_FINANCE_MANAGER", details.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void getPassword_returnsUserPasswordHash() {
        User u = buildUser(AccountStatus.ACTIVE, RoleType.ATTENDEE, null);
        EventOpsUserDetails details = new EventOpsUserDetails(u);
        assertEquals("hash-value", details.getPassword());
    }

    @Test
    void getUsername_returnsUsername() {
        User u = buildUser(AccountStatus.ACTIVE, RoleType.ATTENDEE, null);
        EventOpsUserDetails details = new EventOpsUserDetails(u);
        assertEquals("alice", details.getUsername());
    }

    @Test
    void isAccountNonLocked_trueForActiveNoLockout() {
        User u = buildUser(AccountStatus.ACTIVE, RoleType.ATTENDEE, null);
        assertTrue(new EventOpsUserDetails(u).isAccountNonLocked());
    }

    @Test
    void isAccountNonLocked_falseWhenLocked() {
        User u = buildUser(AccountStatus.LOCKED, RoleType.ATTENDEE, null);
        assertFalse(new EventOpsUserDetails(u).isAccountNonLocked());
    }

    @Test
    void isAccountNonLocked_falseWhenDisabled() {
        User u = buildUser(AccountStatus.DISABLED, RoleType.ATTENDEE, null);
        assertFalse(new EventOpsUserDetails(u).isAccountNonLocked());
    }

    @Test
    void isAccountNonLocked_falseWhenLockoutInFuture() {
        User u = buildUser(AccountStatus.ACTIVE, RoleType.ATTENDEE, Instant.now().plusSeconds(600));
        assertFalse(new EventOpsUserDetails(u).isAccountNonLocked());
    }

    @Test
    void isAccountNonLocked_trueWhenLockoutInPast() {
        User u = buildUser(AccountStatus.ACTIVE, RoleType.ATTENDEE, Instant.now().minusSeconds(600));
        assertTrue(new EventOpsUserDetails(u).isAccountNonLocked());
    }

    @Test
    void isEnabled_falseWhenDisabled() {
        User u = buildUser(AccountStatus.DISABLED, RoleType.ATTENDEE, null);
        assertFalse(new EventOpsUserDetails(u).isEnabled());
    }

    @Test
    void isEnabled_trueForActiveAndLocked() {
        assertTrue(new EventOpsUserDetails(buildUser(AccountStatus.ACTIVE, RoleType.ATTENDEE, null)).isEnabled());
        assertTrue(new EventOpsUserDetails(buildUser(AccountStatus.LOCKED, RoleType.ATTENDEE, null)).isEnabled());
    }

    @Test
    void isAccountNonExpired_alwaysTrue() {
        User u = buildUser(AccountStatus.ACTIVE, RoleType.ATTENDEE, null);
        assertTrue(new EventOpsUserDetails(u).isAccountNonExpired());
    }

    @Test
    void isCredentialsNonExpired_alwaysTrue() {
        User u = buildUser(AccountStatus.ACTIVE, RoleType.ATTENDEE, null);
        assertTrue(new EventOpsUserDetails(u).isCredentialsNonExpired());
    }

    @Test
    void getUser_returnsWrappedEntity() {
        User u = buildUser(AccountStatus.ACTIVE, RoleType.ATTENDEE, null);
        EventOpsUserDetails details = new EventOpsUserDetails(u);
        assertSame(u, details.getUser());
    }
}
