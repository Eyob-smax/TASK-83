package com.eventops.repository.finance;

import com.eventops.domain.finance.PostingJournal;
import com.eventops.domain.finance.PostingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;

public interface PostingJournalRepository extends JpaRepository<PostingJournal, String>, JpaSpecificationExecutor<PostingJournal> {
    List<PostingJournal> findByPeriodId(String periodId);
    List<PostingJournal> findByStatus(PostingStatus status);
}
