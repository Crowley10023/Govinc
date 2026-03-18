package com.govinc.reporting;

import com.govinc.assessment.AssessmentControlAnswer;
import com.govinc.maturity.MaturityAnswer;

import java.util.*;

/**
 * Stateless calculation helpers for org-unit reporting.
 * Extracted so they can be unit-tested without a Spring context.
 */
public final class ReportingCalculator {

    private ReportingCalculator() {}

    /**
     * Result of evaluating a set of control answers for one assessment.
     */
    public static class AssessmentStats {
        /** Average maturity rating (0–100), rounded to 1 decimal. 0.0 if no answers. */
        public final double averageRating;
        /** Number of distinct security controls that have a non-null maturity answer. */
        public final int answeredControls;
        /** Coverage percentage (answeredControls / totalControls * 100), capped at 100. */
        public final double coveragePercent;

        AssessmentStats(double averageRating, int answeredControls, double coveragePercent) {
            this.averageRating = averageRating;
            this.answeredControls = answeredControls;
            this.coveragePercent = coveragePercent;
        }
    }

    /**
     * Computes maturity statistics from a set of control answers.
     *
     * <p>When multiple {@link AssessmentControlAnswer} rows exist for the same
     * {@code SecurityControl} (e.g. an org-service default + a user override),
     * the override entry (isOverride == true) wins; otherwise the last entry wins.
     * This prevents overcounting that would produce coverage {@literal >} 100 %.
     *
     * @param controlAnswers all answer rows from {@code AssessmentDetails.getControlAnswers()}
     * @param totalControls  number of controls in the security catalog
     * @return computed stats; never {@code null}
     */
    public static AssessmentStats compute(
            Collection<AssessmentControlAnswer> controlAnswers,
            int totalControls) {

        if (controlAnswers == null || controlAnswers.isEmpty() || totalControls <= 0) {
            return new AssessmentStats(0.0, 0, 0.0);
        }

        // Deduplicate: one effective answer per SecurityControl.
        // Prefer isOverride=true; among equals keep whichever is encountered last.
        Map<Long, AssessmentControlAnswer> effective = new LinkedHashMap<>();
        for (AssessmentControlAnswer aca : controlAnswers) {
            if (aca.getSecurityControl() == null) continue;
            Long controlId = aca.getSecurityControl().getId();
            AssessmentControlAnswer existing = effective.get(controlId);
            if (existing == null) {
                effective.put(controlId, aca);
            } else {
                // Override answer takes priority over a non-override answer
                boolean incomingIsOverride = Boolean.TRUE.equals(aca.getIsOverride());
                boolean existingIsOverride = Boolean.TRUE.equals(existing.getIsOverride());
                if (incomingIsOverride && !existingIsOverride) {
                    effective.put(controlId, aca);
                }
            }
        }

        int ratingSum = 0;
        int answeredCount = 0;
        for (AssessmentControlAnswer aca : effective.values()) {
            MaturityAnswer ma = aca.getMaturityAnswer();
            if (ma != null) {
                ratingSum += ma.getRating();
                answeredCount++;
            }
        }

        if (answeredCount == 0) {
            return new AssessmentStats(0.0, 0, 0.0);
        }

        double avg = Math.round((double) ratingSum / answeredCount * 10.0) / 10.0;
        double rawCoverage = (double) answeredCount / totalControls * 100.0;
        double coverage = Math.min(100.0, Math.round(rawCoverage * 10.0) / 10.0);

        return new AssessmentStats(avg, answeredCount, coverage);
    }
}
