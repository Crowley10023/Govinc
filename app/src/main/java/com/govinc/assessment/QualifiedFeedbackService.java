package com.govinc.assessment;

import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QualifiedFeedbackService {
    private final QualifiedFeedbackRepository repository;

    public QualifiedFeedbackService(QualifiedFeedbackRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Map<Long, String> ratings(Long assessmentId) {
        return repository.findByAssessmentId(assessmentId).stream().collect(Collectors.toMap(
                feedback -> feedback.getSecurityControl().getId(), feedback -> feedback.getRating().name()));
    }

    @Transactional
    public void save(Assessment assessment, com.govinc.catalog.SecurityControl control, QualifiedFeedback.Rating rating) {
        var existing = repository.findByAssessmentIdAndSecurityControlId(assessment.getId(), control.getId());
        if (rating == null) {
            existing.ifPresent(repository::delete);
        } else if (existing.isPresent()) {
            existing.get().setRating(rating);
        } else {
            repository.save(new QualifiedFeedback(assessment, control, rating));
        }
    }
}