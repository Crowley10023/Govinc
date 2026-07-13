package com.govinc.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AssessmentRepository extends JpaRepository<Assessment, Long> {
    @Query("SELECT COUNT(a) FROM Assessment a WHERE a.securityCatalog.id = :catalogId")
    long countBySecurityCatalogId(@Param("catalogId") Long catalogId);

    boolean existsByOrgUnitId(Long orgUnitId);

    long countByOrgUnitId(Long orgUnitId);

    List<Assessment> findByUsersId(Long userId);

    List<Assessment> findByCreatedById(Long userId);

    List<Assessment> findBySecurityCatalogId(Long catalogId);

    List<Assessment> findBySecurityCatalogIdAndOrgUnitId(Long catalogId, Long orgUnitId);

    @Query("SELECT DISTINCT a FROM Assessment a JOIN a.orgServices s WHERE s.id = :orgServiceId AND a.status = com.govinc.assessment.AssessmentStatus.OPEN")
    List<Assessment> findOpenByOrgServiceId(@Param("orgServiceId") Long orgServiceId);
}
