package com.govinc.reporting;

import com.govinc.assessment.AssessmentControlAnswer;

import java.util.*;

/**
 * Stateless utility for capability-scoped score calculation.
 * Delegates per-bucket compute to {@link ReportingCalculator}.
 */
public final class CapabilityCalculator {

    private CapabilityCalculator() {}

    /**
     * Filters the given pool of effective answers to only those whose
     * security-control ID is in {@code controlIds}, then delegates to
     * {@link ReportingCalculator#compute}.
     *
     * @param effectiveAnswers deduplicated map: controlId → best answer
     * @param controlIds       the set of control IDs belonging to this bucket
     * @return computed stats
     */
    public static ReportingCalculator.AssessmentStats computeForControls(
            Map<Long, AssessmentControlAnswer> effectiveAnswers,
            Set<Long> controlIds) {

        if (controlIds == null || controlIds.isEmpty()) {
            return new ReportingCalculator.AssessmentStats(0.0, 0, 0.0);
        }

        List<AssessmentControlAnswer> filtered = new ArrayList<>();
        for (Long cid : controlIds) {
            AssessmentControlAnswer aca = effectiveAnswers.get(cid);
            if (aca != null) {
                filtered.add(aca);
            }
        }

        return ReportingCalculator.compute(filtered, controlIds.size());
    }

    /**
     * Builds a deduplicated map of the best (override-preferred) control answer
     * from a flat collection spanning multiple assessments.
     * Override answers always beat non-override answers for the same control.
     */
    public static Map<Long, AssessmentControlAnswer> deduplicateAnswers(
            Collection<AssessmentControlAnswer> allAnswers) {

        Map<Long, AssessmentControlAnswer> effective = new LinkedHashMap<>();
        if (allAnswers == null) return effective;

        for (AssessmentControlAnswer aca : allAnswers) {
            if (aca.getSecurityControl() == null) continue;
            Long controlId = aca.getSecurityControl().getId();
            AssessmentControlAnswer existing = effective.get(controlId);
            if (existing == null) {
                effective.put(controlId, aca);
            } else {
                boolean incomingOverride = Boolean.TRUE.equals(aca.getIsOverride());
                boolean existingOverride = Boolean.TRUE.equals(existing.getIsOverride());
                if (incomingOverride && !existingOverride) {
                    effective.put(controlId, aca);
                }
            }
        }
        return effective;
    }
}
