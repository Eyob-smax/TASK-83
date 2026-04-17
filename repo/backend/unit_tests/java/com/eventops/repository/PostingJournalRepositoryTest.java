package com.eventops.repository;

import com.eventops.domain.finance.PostingJournal;
import com.eventops.domain.finance.PostingStatus;
import com.eventops.repository.finance.PostingJournalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class PostingJournalRepositoryTest {

    @Autowired
    private PostingJournalRepository postingJournalRepository;

    @BeforeEach
    void clean() {
        postingJournalRepository.deleteAll();
    }

    private PostingJournal build(String periodId, String ruleId, BigDecimal amount, PostingStatus status) {
        PostingJournal j = new PostingJournal();
        j.setPeriodId(periodId);
        j.setRuleId(ruleId);
        j.setRuleVersion(1);
        j.setTotalAmount(amount);
        j.setStatus(status);
        j.setPostedBy("admin");
        j.setSessionId("session1");
        j.setDescription("test posting");
        return j;
    }

    @Test
    void saveAndFindById_roundTrip() {
        PostingJournal saved = postingJournalRepository.save(
                build("p1", "rule1", new BigDecimal("100.00"), PostingStatus.POSTED));
        Optional<PostingJournal> found = postingJournalRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals(0, new BigDecimal("100.00").compareTo(found.get().getTotalAmount()));
    }

    @Test
    void findByPeriodId_returnsAllForPeriod() {
        postingJournalRepository.save(build("p1", "rule1", new BigDecimal("100"), PostingStatus.POSTED));
        postingJournalRepository.save(build("p1", "rule2", new BigDecimal("200"), PostingStatus.DRAFT));
        postingJournalRepository.save(build("p2", "rule1", new BigDecimal("300"), PostingStatus.POSTED));

        List<PostingJournal> p1 = postingJournalRepository.findByPeriodId("p1");
        assertEquals(2, p1.size());
    }

    @Test
    void findByStatus_filtersCorrectly() {
        postingJournalRepository.save(build("p1", "rule1", new BigDecimal("100"), PostingStatus.POSTED));
        postingJournalRepository.save(build("p1", "rule2", new BigDecimal("200"), PostingStatus.POSTED));
        postingJournalRepository.save(build("p1", "rule3", new BigDecimal("50"), PostingStatus.DRAFT));
        postingJournalRepository.save(build("p1", "rule4", new BigDecimal("75"), PostingStatus.REVERSED));

        assertEquals(2, postingJournalRepository.findByStatus(PostingStatus.POSTED).size());
        assertEquals(1, postingJournalRepository.findByStatus(PostingStatus.DRAFT).size());
        assertEquals(1, postingJournalRepository.findByStatus(PostingStatus.REVERSED).size());
        assertEquals(0, postingJournalRepository.findByStatus(PostingStatus.FAILED).size());
    }
}
