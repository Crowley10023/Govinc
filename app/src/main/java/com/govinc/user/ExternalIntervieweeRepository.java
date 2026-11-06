package com.govinc.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExternalIntervieweeRepository extends JpaRepository<ExternalInterviewee, Long> {
    Optional<ExternalInterviewee> findByEmailIgnoreCase(String email);

    @Modifying
    @Query(value = "DELETE FROM assessment_external_interviewees WHERE external_interviewee_id = :id", nativeQuery = true)
    void removeAssessmentLinks(@Param("id") Long id);
}