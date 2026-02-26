package com.govinc.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentControlAnswerRepository extends JpaRepository<AssessmentControlAnswer, Long> {
    // counts answers that actually have a maturityAnswer assigned
    long countByMaturityAnswerIsNotNull();
}
