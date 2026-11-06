package com.govinc.assessment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QualifiedFeedbackRepository extends JpaRepository<QualifiedFeedback, Long> {
    List<QualifiedFeedback> findByAssessmentId(Long assessmentId);
    Optional<QualifiedFeedback> findByAssessmentIdAndSecurityControlId(Long assessmentId, Long controlId);
}