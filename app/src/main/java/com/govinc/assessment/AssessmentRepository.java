package com.govinc.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssessmentRepository extends JpaRepository<Assessment, Long> {
    @Query("SELECT COUNT(a) FROM Assessment a WHERE a.securityCatalog.id = :catalogId")
    long countBySecurityCatalogId(@Param("catalogId") Long catalogId);
}
