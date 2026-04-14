package com.eventops.repository.finance;

import com.eventops.domain.finance.AccountingPeriod;
import com.eventops.domain.finance.PeriodStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, String> {
    List<AccountingPeriod> findByStatus(PeriodStatus status);
}
