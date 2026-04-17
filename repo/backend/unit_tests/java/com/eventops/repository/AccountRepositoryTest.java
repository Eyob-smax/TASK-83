package com.eventops.repository;

import com.eventops.domain.finance.Account;
import com.eventops.repository.finance.AccountRepository;
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
class AccountRepositoryTest {

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void clean() {
        accountRepository.deleteAll();
    }

    private Account build(String code, String name, String type, boolean active, String parentId) {
        Account a = new Account();
        a.setAccountCode(code);
        a.setName(name);
        a.setAccountType(type);
        a.setActive(active);
        a.setParentId(parentId);
        return a;
    }

    @Test
    void saveAndFindById_roundTrip() {
        Account saved = accountRepository.save(build("4000", "Revenue", "REVENUE", true, null));
        Optional<Account> found = accountRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("4000", found.get().getAccountCode());
    }

    @Test
    void findByAccountCode_found() {
        accountRepository.save(build("4000", "Revenue", "REVENUE", true, null));
        Optional<Account> found = accountRepository.findByAccountCode("4000");
        assertTrue(found.isPresent());
        assertEquals("Revenue", found.get().getName());
    }

    @Test
    void findByAccountCode_notFound() {
        Optional<Account> found = accountRepository.findByAccountCode("9999");
        assertTrue(found.isEmpty());
    }

    @Test
    void existsByAccountCode_trueWhenExists() {
        accountRepository.save(build("4000", "Revenue", "REVENUE", true, null));
        assertTrue(accountRepository.existsByAccountCode("4000"));
        assertFalse(accountRepository.existsByAccountCode("9999"));
    }

    @Test
    void findByActiveTrue_returnsOnlyActive() {
        accountRepository.save(build("4000", "Revenue", "REVENUE", true, null));
        accountRepository.save(build("4001", "Old Revenue", "REVENUE", false, null));
        accountRepository.save(build("5000", "Expense", "EXPENSE", true, null));

        List<Account> active = accountRepository.findByActiveTrue();
        assertEquals(2, active.size());
    }

    @Test
    void findByParentId_returnsChildren() {
        Account parent = accountRepository.save(build("4000", "Revenue", "REVENUE", true, null));
        accountRepository.save(build("4100", "Course Fees", "REVENUE", true, parent.getId()));
        accountRepository.save(build("4200", "Workshop Fees", "REVENUE", true, parent.getId()));
        accountRepository.save(build("5000", "Expense", "EXPENSE", true, null));

        List<Account> children = accountRepository.findByParentId(parent.getId());
        assertEquals(2, children.size());
    }

    @Test
    void uniqueConstraint_duplicateAccountCode_throws() {
        accountRepository.save(build("4000", "Revenue", "REVENUE", true, null));
        Account duplicate = build("4000", "Other Revenue", "REVENUE", true, null);
        assertThrows(DataIntegrityViolationException.class, () -> accountRepository.saveAndFlush(duplicate));
    }
}
