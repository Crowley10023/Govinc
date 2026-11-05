package com.govinc.organization;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OrgServiceAssessmentVersionRepository extends JpaRepository<OrgServiceAssessmentVersion, Long> {
    List<OrgServiceAssessmentVersion> findByAssessmentIdOrderByVersionNumberAsc(Long assessmentId);

    Optional<OrgServiceAssessmentVersion> findByAssessmentIdAndVersionNumber(Long assessmentId, int versionNumber);
}
