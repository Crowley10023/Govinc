package com.govinc.organization;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrgServiceAssessmentRepository extends JpaRepository<OrgServiceAssessment, Long> {
    List<OrgServiceAssessment> findByOrgServiceId(Long orgServiceId);

    @EntityGraph(attributePaths = { "orgService", "controls", "controls.securityControl" })
    List<OrgServiceAssessment> findByOrgServiceIdIn(List<Long> orgServiceIds);
}
