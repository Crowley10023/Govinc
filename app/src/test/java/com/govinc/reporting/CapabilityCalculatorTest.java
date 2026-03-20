package com.govinc.reporting;

import com.govinc.assessment.AssessmentControlAnswer;
import com.govinc.catalog.SecurityControl;
import com.govinc.maturity.MaturityAnswer;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CapabilityCalculator}.
 * No Spring context required — pure logic tests.
 */
class CapabilityCalculatorTest {

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static SecurityControl control(long id) {
        SecurityControl sc = new SecurityControl();
        sc.setId(id);
        return sc;
    }

    private static MaturityAnswer answer(int rating) {
        MaturityAnswer ma = new MaturityAnswer();
        ma.setRating(rating);
        return ma;
    }

    private static AssessmentControlAnswer aca(SecurityControl sc, MaturityAnswer ma, boolean isOverride) {
        AssessmentControlAnswer a = new AssessmentControlAnswer();
        a.setSecurityControl(sc);
        a.setMaturityAnswer(ma);
        a.setIsOverride(isOverride);
        return a;
    }

    // ─── deduplicateAnswers ───────────────────────────────────────────────────

    @Test
    void dedup_emptyInput_returnsEmptyMap() {
        assertThat(CapabilityCalculator.deduplicateAnswers(Collections.emptyList())).isEmpty();
    }

    @Test
    void dedup_nullInput_returnsEmptyMap() {
        assertThat(CapabilityCalculator.deduplicateAnswers(null)).isEmpty();
    }

    @Test
    void dedup_singleEntry_isRetained() {
        var sc = control(1);
        var result = CapabilityCalculator.deduplicateAnswers(List.of(aca(sc, answer(50), false)));
        assertThat(result).containsKey(1L);
        assertThat(result.get(1L).getMaturityAnswer().getRating()).isEqualTo(50);
    }

    @Test
    void dedup_overrideBeatsNonOverride() {
        var sc = control(1);
        var result = CapabilityCalculator.deduplicateAnswers(
                List.of(aca(sc, answer(20), false), aca(sc, answer(80), true)));
        assertThat(result.get(1L).getMaturityAnswer().getRating()).isEqualTo(80);
    }

    @Test
    void dedup_overrideFirstThenNonOverride_overrideKept() {
        var sc = control(1);
        var result = CapabilityCalculator.deduplicateAnswers(
                List.of(aca(sc, answer(90), true), aca(sc, answer(10), false)));
        assertThat(result.get(1L).getMaturityAnswer().getRating()).isEqualTo(90);
    }

    @Test
    void dedup_twoNonOverrides_firstKept() {
        var sc = control(1);
        var result = CapabilityCalculator.deduplicateAnswers(
                List.of(aca(sc, answer(40), false), aca(sc, answer(70), false)));
        // first wins when both are non-override
        assertThat(result.get(1L).getMaturityAnswer().getRating()).isEqualTo(40);
    }

    @Test
    void dedup_nullSecurityControl_skipped() {
        AssessmentControlAnswer a = new AssessmentControlAnswer();
        a.setSecurityControl(null);
        a.setMaturityAnswer(answer(50));
        assertThat(CapabilityCalculator.deduplicateAnswers(List.of(a))).isEmpty();
    }

    @Test
    void dedup_distinctControls_allRetained() {
        var result = CapabilityCalculator.deduplicateAnswers(List.of(
                aca(control(1), answer(30), false),
                aca(control(2), answer(60), false),
                aca(control(3), answer(90), false)));
        assertThat(result).hasSize(3);
    }

    // ─── computeForControls ──────────────────────────────────────────────────

    @Test
    void compute_emptyControlIds_returnsZeroStats() {
        var effective = CapabilityCalculator.deduplicateAnswers(
                List.of(aca(control(1), answer(80), false)));
        var stats = CapabilityCalculator.computeForControls(effective, Collections.emptySet());
        assertThat(stats.averageRating).isEqualTo(0.0);
        assertThat(stats.answeredControls).isEqualTo(0);
        assertThat(stats.coveragePercent).isEqualTo(0.0);
    }

    @Test
    void compute_nullControlIds_returnsZeroStats() {
        var effective = CapabilityCalculator.deduplicateAnswers(
                List.of(aca(control(1), answer(80), false)));
        var stats = CapabilityCalculator.computeForControls(effective, null);
        assertThat(stats.averageRating).isEqualTo(0.0);
    }

    @Test
    void compute_controlIdsWithNoMatchingAnswers_returnsZeroStats() {
        var effective = CapabilityCalculator.deduplicateAnswers(
                List.of(aca(control(1), answer(80), false)));
        // Looking for control 99 which has no answer
        var stats = CapabilityCalculator.computeForControls(effective, Set.of(99L));
        assertThat(stats.averageRating).isEqualTo(0.0);
        assertThat(stats.answeredControls).isEqualTo(0);
        assertThat(stats.coveragePercent).isEqualTo(0.0);
    }

    @Test
    void compute_singleMatchingControl_correctStats() {
        var effective = CapabilityCalculator.deduplicateAnswers(
                List.of(aca(control(1), answer(60), false)));
        var stats = CapabilityCalculator.computeForControls(effective, Set.of(1L));
        assertThat(stats.averageRating).isEqualTo(60.0);
        assertThat(stats.answeredControls).isEqualTo(1);
        assertThat(stats.coveragePercent).isEqualTo(100.0);
    }

    @Test
    void compute_subsetOfControls_coveragePartial() {
        // 3 controls in domain but only 2 have answers
        var effective = CapabilityCalculator.deduplicateAnswers(List.of(
                aca(control(1), answer(50), false),
                aca(control(2), answer(70), false)));
        var stats = CapabilityCalculator.computeForControls(effective, Set.of(1L, 2L, 3L));
        assertThat(stats.answeredControls).isEqualTo(2);
        assertThat(stats.averageRating).isEqualTo(60.0);
        assertThat(stats.coveragePercent).isEqualTo(66.7);
    }

    @Test
    void compute_overrideUsed_correctScore() {
        var sc = control(1);
        var effective = CapabilityCalculator.deduplicateAnswers(List.of(
                aca(sc, answer(20), false),
                aca(sc, answer(80), true)));
        var stats = CapabilityCalculator.computeForControls(effective, Set.of(1L));
        assertThat(stats.averageRating).isEqualTo(80.0);
    }

    @Test
    void compute_filterExcludesUnrelatedControls() {
        // pool has controls 1, 2, 3 but this capability only owns domains with 1 and 2
        var effective = CapabilityCalculator.deduplicateAnswers(List.of(
                aca(control(1), answer(40), false),
                aca(control(2), answer(60), false),
                aca(control(3), answer(100), false)));
        var stats = CapabilityCalculator.computeForControls(effective, Set.of(1L, 2L));
        // control 3 should NOT affect the score
        assertThat(stats.averageRating).isEqualTo(50.0);
        assertThat(stats.answeredControls).isEqualTo(2);
        assertThat(stats.coveragePercent).isEqualTo(100.0);
    }

    @Test
    void compute_allZeroRatings_scoreIsZero() {
        var effective = CapabilityCalculator.deduplicateAnswers(List.of(
                aca(control(1), answer(0), false)));
        var stats = CapabilityCalculator.computeForControls(effective, Set.of(1L));
        assertThat(stats.averageRating).isEqualTo(0.0);
        assertThat(stats.answeredControls).isEqualTo(1);
    }

    @Test
    void compute_coverageNeverExceeds100() {
        // More answers than control IDs (shouldn't happen, but guard anyway)
        var effective = CapabilityCalculator.deduplicateAnswers(List.of(
                aca(control(1), answer(50), false),
                aca(control(2), answer(50), false)));
        var stats = CapabilityCalculator.computeForControls(effective, Set.of(1L));
        assertThat(stats.coveragePercent).isLessThanOrEqualTo(100.0);
    }
}
