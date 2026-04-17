package com.eventops.repository;

import com.eventops.domain.user.AccountStatus;
import com.eventops.domain.user.RoleType;
import com.eventops.domain.user.User;
import com.eventops.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void clean() {
        userRepository.deleteAll();
    }

    private User buildUser(String id, String username, RoleType role, AccountStatus status) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setPasswordHash("hash");
        u.setDisplayName("Display " + username);
        u.setRoleType(role);
        u.setStatus(status);
        return u;
    }

    @Test
    void saveAndFindById_roundTrip() {
        User saved = userRepository.save(buildUser("u1", "alice", RoleType.ATTENDEE, AccountStatus.ACTIVE));
        Optional<User> found = userRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("alice", found.get().getUsername());
    }

    @Test
    void findByUsername_found() {
        userRepository.save(buildUser("u1", "alice", RoleType.ATTENDEE, AccountStatus.ACTIVE));
        Optional<User> found = userRepository.findByUsername("alice");
        assertTrue(found.isPresent());
        assertEquals("alice", found.get().getUsername());
    }

    @Test
    void findByUsername_notFound() {
        Optional<User> found = userRepository.findByUsername("nonexistent");
        assertTrue(found.isEmpty());
    }

    @Test
    void existsByUsername_trueWhenExists() {
        userRepository.save(buildUser("u1", "alice", RoleType.ATTENDEE, AccountStatus.ACTIVE));
        assertTrue(userRepository.existsByUsername("alice"));
    }

    @Test
    void existsByUsername_falseWhenNotExists() {
        assertFalse(userRepository.existsByUsername("nobody"));
    }

    @Test
    void countByRoleType_returnsCorrectCount() {
        userRepository.save(buildUser("u1", "alice", RoleType.ATTENDEE, AccountStatus.ACTIVE));
        userRepository.save(buildUser("u2", "bob", RoleType.ATTENDEE, AccountStatus.ACTIVE));
        userRepository.save(buildUser("u3", "charlie", RoleType.SYSTEM_ADMIN, AccountStatus.ACTIVE));
        assertEquals(2, userRepository.countByRoleType(RoleType.ATTENDEE));
        assertEquals(1, userRepository.countByRoleType(RoleType.SYSTEM_ADMIN));
        assertEquals(0, userRepository.countByRoleType(RoleType.FINANCE_MANAGER));
    }

    @Test
    void findByStatus_returnsMatchingUsers() {
        userRepository.save(buildUser("u1", "alice", RoleType.ATTENDEE, AccountStatus.ACTIVE));
        userRepository.save(buildUser("u2", "bob", RoleType.ATTENDEE, AccountStatus.LOCKED));
        userRepository.save(buildUser("u3", "charlie", RoleType.ATTENDEE, AccountStatus.ACTIVE));
        List<User> active = userRepository.findByStatus(AccountStatus.ACTIVE);
        assertEquals(2, active.size());
        List<User> locked = userRepository.findByStatus(AccountStatus.LOCKED);
        assertEquals(1, locked.size());
    }

    @Test
    void uniqueConstraint_duplicateUsername_throwsException() {
        userRepository.save(buildUser("u1", "alice", RoleType.ATTENDEE, AccountStatus.ACTIVE));
        User duplicate = buildUser("u2", "alice", RoleType.EVENT_STAFF, AccountStatus.ACTIVE);
        assertThrows(DataIntegrityViolationException.class, () -> {
            userRepository.saveAndFlush(duplicate);
        });
    }
}
