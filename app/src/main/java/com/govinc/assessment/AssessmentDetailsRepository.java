package com.govinc.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AssessmentDetailsRepository extends JpaRepository<AssessmentDetails, Long> {
	@Query("""
		select distinct ad
		from AssessmentDetails ad
		left join fetch ad.assessments a
		left join fetch ad.controlAnswers ca
		left join fetch ca.securityControl
		left join fetch ca.maturityAnswer
		where a.id = :assessmentId
		""")
	Optional<AssessmentDetails> findByAssessmentId(@Param("assessmentId") Long assessmentId);
}
