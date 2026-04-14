package com.eventops.repository.user;

import com.eventops.domain.user.User;
import com.eventops.domain.user.AccountStatus;
import com.eventops.domain.user.RoleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String>, JpaSpecificationExecutor<User> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    long countByRoleType(RoleType roleType);
    java.util.List<User> findByStatus(AccountStatus status);
}
