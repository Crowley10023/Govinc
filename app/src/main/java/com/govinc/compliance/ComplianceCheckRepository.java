package com.govinc.compliance;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ComplianceCheckRepository extends JpaRepository<ComplianceCheck, Long> {
    List<ComplianceCheck> findBySecurityCatalogId(Long catalogId);
}
