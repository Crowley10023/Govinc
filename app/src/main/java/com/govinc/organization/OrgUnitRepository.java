package com.govinc.organization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface OrgUnitRepository extends JpaRepository<OrgUnit, Long> {
    // Fetch the org unit with all its direct children and leader eagerly
    @Query("SELECT u FROM OrgUnit u WHERE u.id = :id")
    Optional<OrgUnit> findByIdWithChildren(@Param("id") Long id);
    
    // Fetch org unit by ID with leader (used for recursive loading)
    @Query("SELECT u FROM OrgUnit u WHERE u.id = :id")
    Optional<OrgUnit> findByIdWithLeader(@Param("id") Long id);

    // Get all direct children for a given parent org unit ID
    List<OrgUnit> findByParentId(Long parentId);
}
