package com.govinc.controller;

import com.govinc.assessment.Assessment;
import com.govinc.assessment.AssessmentControlAnswer;
import com.govinc.assessment.AssessmentDetails;
import com.govinc.assessment.AssessmentDetailsService;
import com.govinc.assessment.AssessmentRepository;
import com.govinc.catalog.SecurityCatalog;
import com.govinc.catalog.SecurityCatalogRepository;
import com.govinc.catalog.SecurityControl;
import com.govinc.service.ExternalApiKeyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/public-api/v1")
public class ExternalAssessmentApiController {

    @Autowired
    private SecurityCatalogRepository securityCatalogRepository;

    @Autowired
    private AssessmentRepository assessmentRepository;

    @Autowired
    private AssessmentDetailsService assessmentDetailsService;

    @Autowired
    private ExternalApiKeyService externalApiKeyService;

    @GetMapping("/catalogs")
    public ResponseEntity<?> listCatalogs(HttpServletRequest request) {
        if (!isAuthorized(request)) {
            return unauthorized();
        }

        List<Map<String, Object>> out = securityCatalogRepository.findAll().stream()
                .sorted(Comparator.comparing(SecurityCatalog::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(c -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", c.getId());
                    m.put("name", c.getName());
                    m.put("revision", c.getRevision());
                    m.put("headline", c.getHeadline());
                    return m;
                })
                .toList();

        return ResponseEntity.ok(Map.of("catalogs", out));
    }

    @GetMapping("/catalogs/{catalogId}/assessments")
    public ResponseEntity<?> listAssessmentsForCatalog(@PathVariable Long catalogId, HttpServletRequest request) {
        if (!isAuthorized(request)) {
            return unauthorized();
        }

        if (!securityCatalogRepository.existsById(catalogId)) {
            return ResponseEntity.status(404).body(Map.of("error", "Security catalog not found"));
        }

        List<Map<String, Object>> assessments = assessmentRepository.findBySecurityCatalogId(catalogId).stream()
                .sorted(Comparator.comparing(Assessment::getCreationDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(a -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", a.getId());
                    m.put("name", a.getName());
                    m.put("status", a.getStatus() != null ? a.getStatus().name() : null);
                    m.put("creationDate", a.getCreationDate());
                    m.put("closeDate", a.getCloseDate());
                    m.put("orgUnit", a.getOrgUnit() != null ? a.getOrgUnit().getName() : null);
                    return m;
                })
                .toList();

        return ResponseEntity.ok(Map.of("catalogId", catalogId, "assessments", assessments));
    }

    @GetMapping("/assessments/{assessmentId}/answers")
    public ResponseEntity<?> getAssessmentAnswers(@PathVariable Long assessmentId, HttpServletRequest request) {
        if (!isAuthorized(request)) {
            return unauthorized();
        }

        Optional<Assessment> maybeAssessment = assessmentRepository.findById(assessmentId);
        if (maybeAssessment.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Assessment not found"));
        }

        Assessment assessment = maybeAssessment.get();
        Optional<AssessmentDetails> detailsOpt = assessmentDetailsService.findByAssessmentId(assessmentId);

        Map<Long, AssessmentControlAnswer> answerByControlId = new HashMap<>();
        if (detailsOpt.isPresent() && detailsOpt.get().getControlAnswers() != null) {
            for (AssessmentControlAnswer answer : detailsOpt.get().getControlAnswers()) {
                if (answer.getSecurityControl() != null && answer.getSecurityControl().getId() != null) {
                    answerByControlId.put(answer.getSecurityControl().getId(), answer);
                }
            }
        }

        List<Map<String, Object>> answers = new ArrayList<>();
        for (SecurityControl control : assessment.getEffectiveControls()) {
            AssessmentControlAnswer controlAnswer = answerByControlId.get(control.getId());
            Map<String, Object> row = new HashMap<>();
            row.put("controlId", control.getId());
            row.put("controlName", control.getName());
            row.put("controlDetail", control.getDetail());
            row.put("domainId", control.getSecurityControlDomain() != null ? control.getSecurityControlDomain().getId() : null);
            row.put("domainName", control.getSecurityControlDomain() != null ? control.getSecurityControlDomain().getName() : null);

            if (controlAnswer != null && controlAnswer.getMaturityAnswer() != null) {
                row.put("answerId", controlAnswer.getMaturityAnswer().getId());
                row.put("answer", controlAnswer.getMaturityAnswer().getAnswer());
                row.put("rating", controlAnswer.getMaturityAnswer().getRating());
            } else {
                row.put("answerId", null);
                row.put("answer", null);
                row.put("rating", null);
            }

            row.put("comment", controlAnswer != null ? controlAnswer.getComment() : null);
            row.put("notApplicable", controlAnswer != null && Boolean.TRUE.equals(controlAnswer.getIsNotApplicable()));
            row.put("override", controlAnswer != null && Boolean.TRUE.equals(controlAnswer.getIsOverride()));
            answers.add(row);
        }

        Map<String, Object> assessmentInfo = new HashMap<>();
        assessmentInfo.put("id", assessment.getId());
        assessmentInfo.put("name", assessment.getName());
        assessmentInfo.put("status", assessment.getStatus() != null ? assessment.getStatus().name() : null);
        assessmentInfo.put("creationDate", assessment.getCreationDate());
        assessmentInfo.put("closeDate", assessment.getCloseDate());
        assessmentInfo.put("catalogId", assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getId() : null);
        assessmentInfo.put("catalogName", assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getName() : null);

        return ResponseEntity.ok(Map.of(
                "assessment", assessmentInfo,
                "answers", answers
        ));
    }

    private boolean isAuthorized(HttpServletRequest request) {
        String apiKey = request.getHeader("X-API-Key");
        return externalApiKeyService.validateAndTouch(apiKey);
    }

    private ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(401).body(Map.of(
                "error", "Unauthorized",
                "message", "Provide a valid API key in the X-API-Key header"
        ));
    }
}
