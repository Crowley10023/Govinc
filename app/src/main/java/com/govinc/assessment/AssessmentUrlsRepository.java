package com.govinc.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Optional;

public interface AssessmentUrlsRepository extends JpaRepository<AssessmentUrls, Long> {
    Optional<AssessmentUrls> findByUrl(String url);
    Optional<AssessmentUrls> findByAssessment_Id(Long assessmentId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM AssessmentUrls a WHERE a.assessment.id = :assessmentId")
    void deleteByAssessmentId(@Param("assessmentId") Long assessmentId);

    @Modifying
    @Query("update AssessmentUrls a set a.createdAt = :createdAt where a.id = :id")
    void updateCreatedAtById(@Param("id") Long id, @Param("createdAt") LocalDateTime createdAt);
}
