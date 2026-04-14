package com.eventops.repository.finance;

import com.eventops.domain.finance.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface AccountRepository extends JpaRepository<Account, String> {
    Optional<Account> findByAccountCode(String accountCode);
    boolean existsByAccountCode(String accountCode);
    List<Account> findByActiveTrue();
    List<Account> findByParentId(String parentId);
}
