package com.govinc.organization;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrgServiceAssessmentRepository extends JpaRepository<OrgServiceAssessment, Long> {
    List<OrgServiceAssessment> findByOrgServiceId(Long orgServiceId);
}
