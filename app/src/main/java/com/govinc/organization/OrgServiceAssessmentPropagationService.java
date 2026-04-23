package com.govinc.organization;

import com.govinc.assessment.Assessment;
import com.govinc.assessment.AssessmentControlAnswer;
import com.govinc.assessment.AssessmentControlAnswerRepository;
import com.govinc.assessment.AssessmentDetails;
import com.govinc.assessment.AssessmentDetailsRepository;
import com.govinc.assessment.AssessmentRepository;
import com.govinc.catalog.SecurityControl;
import com.govinc.catalog.SecurityControlRepository;
import com.govinc.maturity.MaturityAnswer;
import com.govinc.maturity.MaturityModel;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Eagerly propagates OrgServiceAssessmentControl changes to all OPEN assessments
 * that include the affected org service.  CLOSED assessments (snapshots) are
 * intentionally not touched.
 */
@Service
public class OrgServiceAssessmentPropagationService {

    private final AssessmentRepository assessmentRepository;
    private final AssessmentDetailsRepository assessmentDetailsRepository;
    private final AssessmentControlAnswerRepository assessmentControlAnswerRepository;
    private final SecurityControlRepository securityControlRepository;
    private final OrgServiceAssessmentRepository orgServiceAssessmentRepository;

    public OrgServiceAssessmentPropagationService(
            AssessmentRepository assessmentRepository,
            AssessmentDetailsRepository assessmentDetailsRepository,
            AssessmentControlAnswerRepository assessmentControlAnswerRepository,
            SecurityControlRepository securityControlRepository,
            OrgServiceAssessmentRepository orgServiceAssessmentRepository) {
        this.assessmentRepository = assessmentRepository;
        this.assessmentDetailsRepository = assessmentDetailsRepository;
        this.assessmentControlAnswerRepository = assessmentControlAnswerRepository;
        this.securityControlRepository = securityControlRepository;
        this.orgServiceAssessmentRepository = orgServiceAssessmentRepository;
    }

    /**
     * Called after an org-service control is saved.  Propagates the updated
     * applicable/percent values (and optionally comment) to every OPEN assessment
     * that links to this org service.
     *
     * @param orgServiceId the org service whose assessment was just modified
     * @param changedControlId the security control that changed
     */
    @Transactional
    public void propagateControlChange(Long orgServiceId, Long changedControlId) {
        List<Assessment> openAssessments = assessmentRepository.findOpenByOrgServiceId(orgServiceId);
        if (openAssessments.isEmpty()) {
            return;
        }

        // Load the current state of ALL org-service assessments linked to this service
        // (there is only one per service, but we use the list API for safety)
        List<OrgServiceAssessment> orgServiceAssessments =
                orgServiceAssessmentRepository.findByOrgServiceId(orgServiceId);
        if (orgServiceAssessments.isEmpty()) {
            return;
        }
        OrgServiceAssessment osa = orgServiceAssessments.get(0);

        // Find the control entry for the changed control
        OrgServiceAssessmentControl changedOsac = osa.getControls().stream()
                .filter(c -> c.getSecurityControl() != null &&
                        c.getSecurityControl().getId().equals(changedControlId))
                .findFirst()
                .orElse(null);

        if (changedOsac == null) {
            return;
        }

        for (Assessment assessment : openAssessments) {
            if (assessment.getSecurityCatalog() == null ||
                    assessment.getSecurityCatalog().getMaturityModel() == null) {
                continue;
            }

            // Only propagate for controls that are within the scope of this assessment
            boolean controlInScope = assessment.getEffectiveControls().stream()
                    .anyMatch(sc -> sc.getId().equals(changedControlId));
            if (!controlInScope) {
                continue;
            }

            Optional<AssessmentDetails> detailsOpt =
                    assessmentDetailsRepository.findByAssessmentId(assessment.getId());
            AssessmentDetails details;
            if (detailsOpt.isPresent()) {
                details = detailsOpt.get();
            } else {
                details = new AssessmentDetails();
                Set<Assessment> aSet = new HashSet<>();
                aSet.add(assessment);
                details.setAssessments(aSet);
                details.setDate(LocalDate.now());
            }

            Set<AssessmentControlAnswer> answers = details.getControlAnswers();

            // Find existing answer for this control (if any)
            AssessmentControlAnswer existing = answers.stream()
                    .filter(a -> a.getSecurityControl() != null &&
                            a.getSecurityControl().getId().equals(changedControlId))
                    .findFirst()
                    .orElse(null);

            // Never overwrite a user's manual override
            if (existing != null && Boolean.TRUE.equals(existing.getIsOverride())) {
                continue;
            }

            if (changedOsac.isApplicable() && changedOsac.getPercent() >= 0) {
                // Control is now applicable: determine the closest maturity answer
                MaturityAnswer closest = findClosestMaturityAnswer(
                        assessment.getSecurityCatalog().getMaturityModel(),
                        changedOsac.getPercent());
                if (closest == null) {
                    continue;
                }

                if (existing != null) {
                    boolean answerChanged = !closest.getId().equals(
                            existing.getMaturityAnswer() != null
                                    ? existing.getMaturityAnswer().getId()
                                    : null);
                    boolean commentChanged = !java.util.Objects.equals(
                            changedOsac.getComment(), existing.getComment());
                    if (answerChanged) {
                        existing.setMaturityAnswer(closest);
                    }
                    if (commentChanged && (existing.getComment() == null
                            || existing.getComment().isEmpty())) {
                        // Only propagate comment when the user has not written their own
                        existing.setComment(changedOsac.getComment());
                    }
                    if (answerChanged || commentChanged) {
                        assessmentControlAnswerRepository.save(existing);
                    }
                } else {
                    SecurityControl control =
                            securityControlRepository.findById(changedControlId).orElse(null);
                    if (control == null) {
                        continue;
                    }
                    AssessmentControlAnswer newAca =
                            new AssessmentControlAnswer(control, closest,
                                    changedOsac.getComment());
                    newAca.setIsOverride(false);
                    newAca = assessmentControlAnswerRepository.save(newAca);
                    answers.add(newAca);
                    assessmentDetailsRepository.save(details);
                }
            } else {
                // Control is no longer applicable in this org service:
                // remove the inherited answer so the control is shown as unanswered.
                if (existing != null) {
                    answers.remove(existing);
                    assessmentControlAnswerRepository.delete(existing);
                    assessmentDetailsRepository.save(details);
                }
            }
        }
    }

    /**
     * Called after an org-service control comment is saved.  Propagates the
     * updated comment to every OPEN assessment (unless the user has overridden).
     */
    @Transactional
    public void propagateCommentChange(Long orgServiceId, Long changedControlId) {
        List<Assessment> openAssessments = assessmentRepository.findOpenByOrgServiceId(orgServiceId);
        if (openAssessments.isEmpty()) {
            return;
        }

        List<OrgServiceAssessment> orgServiceAssessments =
                orgServiceAssessmentRepository.findByOrgServiceId(orgServiceId);
        if (orgServiceAssessments.isEmpty()) {
            return;
        }
        OrgServiceAssessment osa = orgServiceAssessments.get(0);

        OrgServiceAssessmentControl changedOsac = osa.getControls().stream()
                .filter(c -> c.getSecurityControl() != null &&
                        c.getSecurityControl().getId().equals(changedControlId))
                .findFirst()
                .orElse(null);

        if (changedOsac == null || !changedOsac.isApplicable()) {
            return;
        }

        for (Assessment assessment : openAssessments) {
            Optional<AssessmentDetails> detailsOpt =
                    assessmentDetailsRepository.findByAssessmentId(assessment.getId());
            if (detailsOpt.isEmpty()) {
                continue;
            }
            AssessmentDetails details = detailsOpt.get();

            AssessmentControlAnswer existing = details.getControlAnswers().stream()
                    .filter(a -> a.getSecurityControl() != null &&
                            a.getSecurityControl().getId().equals(changedControlId))
                    .findFirst()
                    .orElse(null);

            if (existing == null || Boolean.TRUE.equals(existing.getIsOverride())) {
                continue;
            }

            // Only propagate when the assessment has no user-authored comment
            if (existing.getComment() == null || existing.getComment().isEmpty()) {
                existing.setComment(changedOsac.getComment());
                assessmentControlAnswerRepository.save(existing);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static MaturityAnswer findClosestMaturityAnswer(MaturityModel maturityModel, int percent) {
        if (maturityModel == null || maturityModel.getMaturityAnswers() == null
                || maturityModel.getMaturityAnswers().isEmpty()) {
            return null;
        }
        List<MaturityAnswer> answers = new ArrayList<>(maturityModel.getMaturityAnswers());
        MaturityAnswer closest = answers.get(0);
        int minDiff = Math.abs(closest.getRating() - percent);
        for (MaturityAnswer ans : answers) {
            int diff = Math.abs(ans.getRating() - percent);
            if (diff < minDiff) {
                minDiff = diff;
                closest = ans;
            }
        }
        return closest;
    }
}
