package com.eventops.repository;

import com.eventops.domain.finance.AccountingPeriod;
import com.eventops.domain.finance.PeriodStatus;
import com.eventops.repository.finance.AccountingPeriodRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class AccountingPeriodRepositoryTest {

    @Autowired
    private AccountingPeriodRepository accountingPeriodRepository;

    @BeforeEach
    void clean() {
        accountingPeriodRepository.deleteAll();
    }

    private AccountingPeriod build(String name, PeriodStatus status, LocalDate start, LocalDate end) {
        AccountingPeriod p = new AccountingPeriod();
        p.setName(name);
        p.setStatus(status);
        p.setStartDate(start);
        p.setEndDate(end);
        return p;
    }

    @Test
    void saveAndFindById_roundTrip() {
        AccountingPeriod saved = accountingPeriodRepository.save(
                build("2026-Q1", PeriodStatus.OPEN, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)));
        Optional<AccountingPeriod> found = accountingPeriodRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("2026-Q1", found.get().getName());
        assertEquals(PeriodStatus.OPEN, found.get().getStatus());
    }

    @Test
    void findByStatus_filtersCorrectly() {
        accountingPeriodRepository.save(build("2026-Q1", PeriodStatus.OPEN,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)));
        accountingPeriodRepository.save(build("2026-Q2", PeriodStatus.OPEN,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30)));
        accountingPeriodRepository.save(build("2025-Q4", PeriodStatus.CLOSED,
                LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31)));
        accountingPeriodRepository.save(build("2025-Q3", PeriodStatus.LOCKED,
                LocalDate.of(2025, 7, 1), LocalDate.of(2025, 9, 30)));

        assertEquals(2, accountingPeriodRepository.findByStatus(PeriodStatus.OPEN).size());
        assertEquals(1, accountingPeriodRepository.findByStatus(PeriodStatus.CLOSED).size());
        assertEquals(1, accountingPeriodRepository.findByStatus(PeriodStatus.LOCKED).size());
    }

    @Test
    void findByStatus_emptyWhenNoneMatch() {
        accountingPeriodRepository.save(build("2026-Q1", PeriodStatus.OPEN,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)));
        assertEquals(0, accountingPeriodRepository.findByStatus(PeriodStatus.LOCKED).size());
    }
}
