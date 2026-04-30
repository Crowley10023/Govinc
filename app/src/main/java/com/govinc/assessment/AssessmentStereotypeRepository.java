package com.govinc.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AssessmentStereotypeRepository extends JpaRepository<AssessmentStereotype, Long> {
    List<AssessmentStereotype> findBySecurityCatalogId(Long catalogId);
    List<AssessmentStereotype> findBySecurityCatalogIsNull();
}
