package com.govinc.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HistoricSecurityControlRepository extends JpaRepository<HistoricSecurityControl, Long> {
    List<HistoricSecurityControl> findByOriginalControlIdOrderByChangedAtDesc(Long originalControlId);
    HistoricSecurityControl findTopByOriginalControlIdOrderByChangedAtDesc(Long originalControlId);
}
