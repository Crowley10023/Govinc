package com.govinc.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

	/**
	 * Same as {@link #findByAssessmentId(Long)} but returns every match instead of
	 * an {@link Optional}. Used by the consistency layer to detect (and merge)
	 * duplicate {@code AssessmentDetails} rows that point at the same assessment.
	 */
	@Query("""
		select distinct ad
		from AssessmentDetails ad
		left join fetch ad.assessments a
		left join fetch ad.controlAnswers ca
		left join fetch ca.securityControl
		left join fetch ca.maturityAnswer
		where exists (select 1 from ad.assessments a2 where a2.id = :assessmentId)
		""")
	List<AssessmentDetails> findAllForAssessmentId(@Param("assessmentId") Long assessmentId);

	/** AssessmentDetails rows with no assessment link at all — candidates for cleanup. */
	@Query("select ad from AssessmentDetails ad where ad.assessments is empty")
	List<AssessmentDetails> findOrphans();

	/** AssessmentDetails rows linked to more than one assessment — candidates for splitting. */
	@Query("select ad from AssessmentDetails ad where size(ad.assessments) > 1")
	List<AssessmentDetails> findCrossLinked();

	@Query("""
		select distinct ad
		from AssessmentDetails ad
		left join fetch ad.assessments a
		left join fetch ad.controlAnswers ca
		left join fetch ca.securityControl
		left join fetch ca.maturityAnswer
		where a.id in :assessmentIds
		""")
	List<AssessmentDetails> findAllByAssessmentIds(@Param("assessmentIds") List<Long> assessmentIds);

	/**
	 * Defensive cleanup of a legacy join table {@code assessmentdetails_maturityanswer}
	 * that is no longer mapped by the {@link AssessmentDetails} entity but whose
	 * foreign key still prevents row deletion in databases migrated from an
	 * earlier schema. Returns silently if the table does not exist.
	 */
	@Modifying
	@Query(value =
		"DELETE FROM assessmentdetails_maturityanswer WHERE assessmentdetails_id = :id",
		nativeQuery = true)
	int deleteLegacyMaturityAnswerLinks(@Param("id") Long id);
}
