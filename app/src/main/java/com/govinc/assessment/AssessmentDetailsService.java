package com.govinc.assessment;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.govinc.maturity.MaturityAnswer;

@Service
public class AssessmentDetailsService {
    @Autowired
    private AssessmentDetailsRepository repository;

    @Autowired
    private AssessmentDetailsConsistencyService consistencyService;

    public List<AssessmentDetails> findAll() {
        return repository.findAll();
    }

    /**
     * Batch-fetch AssessmentDetails for a list of assessment IDs.
     * Returns a map of assessmentId → AssessmentDetails. Single DB query replaces N individual lookups.
     */
    public Map<Long, AssessmentDetails> findAllByAssessmentIds(List<Long> assessmentIds) {
        if (assessmentIds == null || assessmentIds.isEmpty()) {
            return new HashMap<>();
        }
        List<AssessmentDetails> details = repository.findAllByAssessmentIds(assessmentIds);
        Map<Long, AssessmentDetails> result = new HashMap<>();
        for (AssessmentDetails ad : details) {
            if (ad.getAssessments() != null) {
                for (Assessment a : ad.getAssessments()) {
                    if (a.getId() != null) {
                        result.put(a.getId(), ad);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Find the AssessmentDetails belonging to a specific Assessment.
     *
     * <p>IMPORTANT: callers MUST pass an {@code Assessment.id}, never an
     * {@code AssessmentDetails.id}. The previous implementation of this method
     * accepted either and silently fell through, which caused cross-assessment
     * contamination: when an Assessment's id happened to collide with an
     * unrelated AssessmentDetails' id, edits/comments were written to (and
     * SSE-broadcast for) the wrong assessment.</p>
     *
     * <p>For lookups by the AssessmentDetails' own primary key (only used by
     * the legacy {@code /assessmentdetails/details|edit/{id}} endpoints), call
     * {@link AssessmentDetailsRepository#findById(Object)} directly.</p>
     */
    public Optional<AssessmentDetails> findByAssessmentId(Long assessmentId) {
        if (assessmentId == null) return Optional.empty();
        // Use the list-returning query so we can detect (and trigger repair of)
        // duplicate AssessmentDetails rows pointing at the same assessment, or
        // rows whose assessment-link set is shared with other assessments.
        // The Optional-returning JPQL would throw NonUniqueResultException in
        // the duplicate case, masking the data corruption.
        List<AssessmentDetails> matches = repository.findAllForAssessmentId(assessmentId);
        if (matches.isEmpty()) return Optional.empty();
        if (matches.size() == 1) {
            AssessmentDetails ad = matches.get(0);
            if (ad.getAssessments() != null && ad.getAssessments().size() > 1) {
                return consistencyService.repairForAssessment(assessmentId);
            }
            return Optional.of(ad);
        }
        // More than one details row for the same assessment — must repair.
        return consistencyService.repairForAssessment(assessmentId);
    }

    /**
     * Smarter update: Only modifies, adds, or removes the relevant control answer(s),
     * prevents deleting all answers if only one is added/edited.
     */
    public AssessmentDetails save(AssessmentDetails details) {
        if (details.getId() != null) {
            Optional<AssessmentDetails> existingOpt = repository.findById(details.getId());
            if (existingOpt.isPresent()) {
                AssessmentDetails existing = existingOpt.get();
                // Update non-collection fields
                existing.setDate(details.getDate());
                existing.setAssessments(details.getAssessments());

                Set<AssessmentControlAnswer> incomingAnswers = details.getControlAnswers();
                Set<AssessmentControlAnswer> currentAnswers = existing.getControlAnswers();

                // Build lookup by SecurityControl (assuming 1:1 mapping per assessment-details)
                // Key: SecurityControl id
                // Remove, update, or add as needed
                // Remove those in existing but NOT in incoming
                Set<Long> incomingControlIds = new HashSet<>();
                for (AssessmentControlAnswer incoming : incomingAnswers) {
                    if (incoming.getSecurityControl() != null) {
                        incomingControlIds.add(incoming.getSecurityControl().getId());
                    }
                }
                // Remove answers not present in the incoming set
                currentAnswers.removeIf(existingA -> existingA.getSecurityControl() != null && !incomingControlIds.contains(existingA.getSecurityControl().getId()));

                for (AssessmentControlAnswer incoming : incomingAnswers) {
                    if (incoming.getSecurityControl() == null)
                        continue;
                    AssessmentControlAnswer match = currentAnswers.stream().filter(
                        existA -> existA.getSecurityControl() != null && existA.getSecurityControl().getId().equals(incoming.getSecurityControl().getId())
                    ).findFirst().orElse(null);
                    if (match != null) {
                        // Update the answer value (MaturityAnswer)
                        match.setMaturityAnswer(incoming.getMaturityAnswer());
                    } else {
                        // Add new answer
                        currentAnswers.add(incoming);
                    }
                }
                return repository.save(existing);
            }
        }
        // Otherwise, this is a new entity (no id set yet)
        return repository.save(details);
    }

    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    /**
     * Returns a map of MaturityAnswer.answer to a summary containing count and percentage for each answer in AssessmentDetails.
     */
    public Map<String, Map<String, Object>> computeAnswerSummary(AssessmentDetails details) {
        return computeAnswerSummary(details, null);
    }

    /**
     * Returns answer summary filtered to a specific set of allowed maturity answer IDs.
     * If allowedMaturityAnswerIds is null/empty, all answers are included.
     */
    public Map<String, Map<String, Object>> computeAnswerSummary(AssessmentDetails details, Set<Long> allowedMaturityAnswerIds) {
        Map<String, Integer> counts = new HashMap<>();
        int total = 0;
        Set<Long> allowed = (allowedMaturityAnswerIds == null || allowedMaturityAnswerIds.isEmpty())
            ? null
            : allowedMaturityAnswerIds.stream().filter(id -> id != null).collect(Collectors.toSet());

        if (details != null && details.getControlAnswers() != null) {
            System.out.println(details);
            for (AssessmentControlAnswer ans : details.getControlAnswers()) {                
                MaturityAnswer ma = ans.getMaturityAnswer();
                if (ma != null && ma.getAnswer() != null) {
                    if (allowed != null && (ma.getId() == null || !allowed.contains(ma.getId()))) {
                        continue;
                    }
                    counts.put(ma.getAnswer(), counts.getOrDefault(ma.getAnswer(), 0) + 1);
                    total++;
                }
            }
        }
        Map<String, Map<String, Object>> summary = new HashMap<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            Map<String, Object> info = new HashMap<>();
            info.put("count", e.getValue());
            info.put("percent", total > 0 ? 100.0 * e.getValue() / total : 0.0);
            summary.put(e.getKey(), info);
        }
        return summary;
    }
}
