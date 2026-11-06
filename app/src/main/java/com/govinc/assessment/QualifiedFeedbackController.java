package com.govinc.assessment;

import com.govinc.authorization.AuthorizationService;
import com.govinc.catalog.SecurityControl;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/assessment")
public class QualifiedFeedbackController {
    private final AssessmentRepository assessments;
    private final QualifiedFeedbackService feedback;
    private final AuthorizationService authorization;

    public QualifiedFeedbackController(AssessmentRepository assessments, QualifiedFeedbackService feedback,
                                       AuthorizationService authorization) {
        this.assessments = assessments;
        this.feedback = feedback;
        this.authorization = authorization;
    }

    @PutMapping("/{assessmentId}/control/{controlId}/qualified-feedback")
    @Transactional
    public ResponseEntity<?> save(@PathVariable Long assessmentId, @PathVariable Long controlId,
                                  @RequestBody Map<String, String> body) {
        if ((!authorization.isAdmin() && !authorization.isInformationSecurityManager())
                || !authorization.canAccessAssessment(assessmentId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Assessment assessment = assessments.findById(assessmentId).orElse(null);
        if (assessment == null) return ResponseEntity.notFound().build();
        if (assessment.getSecurityCatalog() == null || !assessment.getSecurityCatalog().isQualifiedFeedbackEnabled()
                || assessment.isClosed()) return ResponseEntity.status(HttpStatus.CONFLICT).build();
        SecurityControl control = assessment.getEffectiveControls().stream()
                .filter(item -> controlId.equals(item.getId())).findFirst().orElse(null);
        if (control == null) return ResponseEntity.notFound().build();
        String value = body.get("rating");
        QualifiedFeedback.Rating rating;
        try {
            rating = value == null || value.isBlank() ? null : QualifiedFeedback.Rating.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
        feedback.save(assessment, control, rating);
        return ResponseEntity.ok().build();
    }
}