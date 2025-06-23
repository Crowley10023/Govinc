package com.govinc.organization;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrgServiceAssessmentControlRepository extends JpaRepository<OrgServiceAssessmentControl, Long> {
    // Custom queries if needed can be added here
}
