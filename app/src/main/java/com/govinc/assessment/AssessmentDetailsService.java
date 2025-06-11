package com.govinc.assessment;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.govinc.maturity.MaturityAnswer;

@Service
public class AssessmentDetailsService {
    @Autowired
    private AssessmentDetailsRepository repository;

    public List<AssessmentDetails> findAll() {
        return repository.findAll();
    }

    public Optional<AssessmentDetails> findById(Long id) {
        return repository.findById(id);
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
        Map<String, Integer> counts = new HashMap<>();
        int total = 0;
        if (details != null && details.getControlAnswers() != null) {
            System.out.println(details);
            for (AssessmentControlAnswer ans : details.getControlAnswers()) {                
                MaturityAnswer ma = ans.getMaturityAnswer();
                if (ma != null && ma.getAnswer() != null) {
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
