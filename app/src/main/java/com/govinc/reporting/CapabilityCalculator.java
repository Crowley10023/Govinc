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
     * For each security control, averages the rating across all supplied answers
     * (spanning multiple assessments / org units). Only answers with a non-null
     * MaturityAnswer are counted.
     *
     * @param allAnswers flat collection of answers from all assessments in scope
     * @return map: controlId → averaged rating (0–100)
     */
    public static Map<Long, Double> averageScoresPerControl(
            Collection<AssessmentControlAnswer> allAnswers) {

        Map<Long, List<Integer>> scoresByControl = new LinkedHashMap<>();
        if (allAnswers != null) {
            for (AssessmentControlAnswer aca : allAnswers) {
                if (aca.getSecurityControl() == null || aca.getMaturityAnswer() == null) continue;
                Long cid = aca.getSecurityControl().getId();
                scoresByControl.computeIfAbsent(cid, k -> new ArrayList<>()).add(aca.getScore());
            }
        }
        Map<Long, Double> averaged = new LinkedHashMap<>();
        for (Map.Entry<Long, List<Integer>> e : scoresByControl.entrySet()) {
            double avg = e.getValue().stream().mapToInt(i -> i).average().orElse(0.0);
            averaged.put(e.getKey(), avg);
        }
        return averaged;
    }

    /**
     * Computes capability/domain stats from pre-averaged per-control scores.
     *
     * @param averagedScores map: controlId → averaged rating (from {@link #averageScoresPerControl})
     * @param controlIds     the set of control IDs belonging to this bucket
     * @return computed stats
     */
    public static ReportingCalculator.AssessmentStats computeForControlsAveraged(
            Map<Long, Double> averagedScores,
            Set<Long> controlIds) {

        if (controlIds == null || controlIds.isEmpty()) {
            return new ReportingCalculator.AssessmentStats(0.0, 0, 0.0);
        }

        double sum = 0.0;
        int answered = 0;
        for (Long cid : controlIds) {
            Double score = averagedScores.get(cid);
            if (score != null) {
                sum += score;
                answered++;
            }
        }

        if (answered == 0) {
            return new ReportingCalculator.AssessmentStats(0.0, 0, 0.0);
        }

        double avg = Math.round(sum / answered * 10.0) / 10.0;
        double coverage = Math.min(100.0,
                Math.round((double) answered / controlIds.size() * 100.0 * 10.0) / 10.0);
        return new ReportingCalculator.AssessmentStats(avg, answered, coverage);
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
