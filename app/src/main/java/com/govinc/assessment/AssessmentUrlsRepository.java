package com.govinc.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AssessmentUrlsRepository extends JpaRepository<AssessmentUrls, Long> {
    Optional<AssessmentUrls> findByUrl(String url);
    Optional<AssessmentUrls> findByAssessment_Id(Long assessmentId);
    void deleteByAssessmentId(Long assessmentId);
}
