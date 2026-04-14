package com.eventops.security.auth;

import com.eventops.domain.user.AccountStatus;
import com.eventops.domain.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Spring Security {@link UserDetails} implementation that wraps the EventOps
 * {@link User} domain entity.
 *
 * <p>Role mapping: the user's {@code RoleType} is converted to a
 * {@link GrantedAuthority} with a {@code ROLE_} prefix
 * (e.g. {@code ROLE_ATTENDEE}).</p>
 *
 * <p>Account locking: the account is considered non-usable when the status is
 * {@code LOCKED} or {@code DISABLED}, or when {@code lockoutUntil} is still
 * in the future.</p>
 */
public class EventOpsUserDetails implements UserDetails {

    private final User user;

    public EventOpsUserDetails(User user) {
        this.user = user;
    }

    /** Returns the wrapped domain entity. */
    public User getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRoleType().name()));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    /**
     * Account is non-locked only when status is {@code ACTIVE} and there is no
     * active lockout window.
     */
    @Override
    public boolean isAccountNonLocked() {
        if (user.getStatus() == AccountStatus.LOCKED || user.getStatus() == AccountStatus.DISABLED) {
            return false;
        }
        if (user.getLockoutUntil() != null && user.getLockoutUntil().isAfter(Instant.now())) {
            return false;
        }
        return true;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.getStatus() != AccountStatus.DISABLED;
    }
}
