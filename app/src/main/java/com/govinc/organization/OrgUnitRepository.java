package com.govinc.organization;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface OrgUnitRepository extends JpaRepository<OrgUnit, Long> {
    // Fetch the org unit and all its direct children eagerly
    @Query("SELECT u FROM OrgUnit u LEFT JOIN FETCH u.children WHERE u.id = :id")
    Optional<OrgUnit> findByIdWithChildren(@Param("id") Long id);
}

