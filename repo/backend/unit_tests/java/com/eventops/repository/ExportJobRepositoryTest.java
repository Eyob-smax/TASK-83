package com.eventops.repository;

import com.eventops.domain.audit.ExportJob;
import com.eventops.domain.audit.ExportStatus;
import com.eventops.repository.audit.ExportJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ExportJobRepositoryTest {

    @Autowired
    private ExportJobRepository exportJobRepository;

    @BeforeEach
    void clean() {
        exportJobRepository.deleteAll();
    }

    private ExportJob build(String exportType, String requestedBy, ExportStatus status) {
        ExportJob j = new ExportJob();
        j.setExportType(exportType);
        j.setRequestedBy(requestedBy);
        j.setStatus(status);
        return j;
    }

    @Test
    void saveAndFindById_roundTrip() {
        ExportJob saved = exportJobRepository.save(build("AUDIT_LOG", "u1", ExportStatus.PENDING));
        Optional<ExportJob> found = exportJobRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("AUDIT_LOG", found.get().getExportType());
        assertEquals(ExportStatus.PENDING, found.get().getStatus());
    }

    @Test
    void findByRequestedBy_returnsAllForRequester() {
        exportJobRepository.save(build("AUDIT_LOG", "u1", ExportStatus.COMPLETED));
        exportJobRepository.save(build("ROSTER", "u1", ExportStatus.PENDING));
        exportJobRepository.save(build("FINANCE_REPORT", "u2", ExportStatus.PENDING));

        List<ExportJob> u1Jobs = exportJobRepository.findByRequestedBy("u1");
        assertEquals(2, u1Jobs.size());
    }

    @Test
    void findByStatus_filtersCorrectly() {
        exportJobRepository.save(build("AUDIT_LOG", "u1", ExportStatus.COMPLETED));
        exportJobRepository.save(build("ROSTER", "u2", ExportStatus.PENDING));
        exportJobRepository.save(build("FINANCE_REPORT", "u3", ExportStatus.COMPLETED));
        exportJobRepository.save(build("ROSTER", "u4", ExportStatus.DENIED));

        assertEquals(2, exportJobRepository.findByStatus(ExportStatus.COMPLETED).size());
        assertEquals(1, exportJobRepository.findByStatus(ExportStatus.PENDING).size());
        assertEquals(1, exportJobRepository.findByStatus(ExportStatus.DENIED).size());
        assertEquals(0, exportJobRepository.findByStatus(ExportStatus.GENERATING).size());
    }
}
