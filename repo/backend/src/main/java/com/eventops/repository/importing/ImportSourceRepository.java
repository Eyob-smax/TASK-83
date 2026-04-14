package com.eventops.repository.importing;

import com.eventops.domain.importing.ImportSource;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ImportSourceRepository extends JpaRepository<ImportSource, String> {
    List<ImportSource> findByActiveTrue();
}
