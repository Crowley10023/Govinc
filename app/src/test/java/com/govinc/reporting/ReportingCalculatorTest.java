package com.govinc.reporting;

import com.govinc.assessment.AssessmentControlAnswer;
import com.govinc.catalog.SecurityControl;
import com.govinc.maturity.MaturityAnswer;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ReportingCalculator}.
 * No Spring context needed — pure logic tests.
 */
class ReportingCalculatorTest {

    // ── helpers ──────────────────────────────────────────────────────────────

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

    // ── empty / edge cases ───────────────────────────────────────────────────

    @Test
    void emptyAnswers_returnsZeros() {
        var stats = ReportingCalculator.compute(Collections.emptyList(), 10);
        assertThat(stats.averageRating).isEqualTo(0.0);
        assertThat(stats.answeredControls).isEqualTo(0);
        assertThat(stats.coveragePercent).isEqualTo(0.0);
    }

    @Test
    void nullAnswers_returnsZeros() {
        var stats = ReportingCalculator.compute(null, 10);
        assertThat(stats.averageRating).isEqualTo(0.0);
        assertThat(stats.answeredControls).isEqualTo(0);
        assertThat(stats.coveragePercent).isEqualTo(0.0);
    }

    @Test
    void zeroTotalControls_returnsZeroCoverage() {
        var sc = control(1);
        var list = List.of(aca(sc, answer(50), false));
        var stats = ReportingCalculator.compute(list, 0);
        assertThat(stats.coveragePercent).isEqualTo(0.0);
    }

    // ── basic calculation ────────────────────────────────────────────────────

    @Test
    void singleAnswer_correctStats() {
        var sc = control(1);
        var list = List.of(aca(sc, answer(60), false));
        var stats = ReportingCalculator.compute(list, 4);

        assertThat(stats.averageRating).isEqualTo(60.0);
        assertThat(stats.answeredControls).isEqualTo(1);
        assertThat(stats.coveragePercent).isEqualTo(25.0); // 1/4
    }

    @Test
    void twoDistinctControls_averageAndCoverage() {
        var sc1 = control(1);
        var sc2 = control(2);
        var list = List.of(
                aca(sc1, answer(40), false),
                aca(sc2, answer(80), false)
        );
        var stats = ReportingCalculator.compute(list, 4);

        assertThat(stats.averageRating).isEqualTo(60.0); // (40+80)/2
        assertThat(stats.answeredControls).isEqualTo(2);
        assertThat(stats.coveragePercent).isEqualTo(50.0); // 2/4
    }

    @Test
    void allControlsAnswered_coverageIs100() {
        var sc1 = control(1);
        var sc2 = control(2);
        var list = List.of(
                aca(sc1, answer(100), false),
                aca(sc2, answer(100), false)
        );
        var stats = ReportingCalculator.compute(list, 2);

        assertThat(stats.coveragePercent).isEqualTo(100.0);
    }

    // ── deduplication (prevents coverage > 100 %) ───────────────────────────

    @Test
    void duplicateControlEntries_countedOnce() {
        // Same control appears twice (org-service answer + user answer without override flag)
        var sc = control(1);
        var list = List.of(
                aca(sc, answer(40), false),
                aca(sc, answer(60), false)
        );
        // totalControls = 1, so without deduplication coverage would be 200 %
        var stats = ReportingCalculator.compute(list, 1);

        assertThat(stats.answeredControls).isEqualTo(1);
        assertThat(stats.coveragePercent).isLessThanOrEqualTo(100.0);
    }

    @Test
    void overrideAnswerTakesPriorityOverNonOverride() {
        var sc = control(1);
        // Non-override first, then override with different rating
        var list = List.of(
                aca(sc, answer(20), false),
                aca(sc, answer(80), true)   // this should win
        );
        var stats = ReportingCalculator.compute(list, 2);

        assertThat(stats.averageRating).isEqualTo(80.0);
        assertThat(stats.answeredControls).isEqualTo(1);
    }

    @Test
    void overrideAnswerWinsEvenWhenSeenFirst() {
        var sc = control(1);
        // Override first, then another non-override answer for same control
        var list = List.of(
                aca(sc, answer(90), true),   // override, seen first
                aca(sc, answer(10), false)   // non-override, must not overwrite
        );
        var stats = ReportingCalculator.compute(list, 2);

        assertThat(stats.averageRating).isEqualTo(90.0);
    }

    @Test
    void mixedControlsSomeWithoutMaturityAnswer_onlyAnsweredCounted() {
        var sc1 = control(1);
        var sc2 = control(2);
        var sc3 = control(3);
        var list = List.of(
                aca(sc1, answer(60), false),
                aca(sc2, null, false),          // no maturity answer
                aca(sc3, answer(80), false)
        );
        var stats = ReportingCalculator.compute(list, 3);

        assertThat(stats.answeredControls).isEqualTo(2);
        assertThat(stats.averageRating).isEqualTo(70.0); // (60+80)/2
        assertThat(stats.coveragePercent).isEqualTo(66.7); // 2/3
    }

    @Test
    void coverageNeverExceeds100() {
        // 5 answers for 2 total controls — can happen with org-service duplicates
        var sc1 = control(1);
        var sc2 = control(2);
        var sc3 = control(3);
        var list = List.of(
                aca(sc1, answer(50), false),
                aca(sc2, answer(50), false),
                aca(sc3, answer(50), false)   // third control, catalog only has 2
        );
        var stats = ReportingCalculator.compute(list, 2);

        assertThat(stats.coveragePercent).isLessThanOrEqualTo(100.0);
    }

    // ── rounding ─────────────────────────────────────────────────────────────

    @Test
    void averageRatingRoundedToOneDecimal() {
        var sc1 = control(1);
        var sc2 = control(2);
        var sc3 = control(3);
        // (10 + 20 + 30) / 3 = 20.0 — exact
        var list = List.of(
                aca(sc1, answer(10), false),
                aca(sc2, answer(20), false),
                aca(sc3, answer(30), false)
        );
        var stats = ReportingCalculator.compute(list, 3);
        assertThat(stats.averageRating).isEqualTo(20.0);
    }

    @Test
    void coverageRoundedToOneDecimal() {
        // 1/3 = 33.333... → 33.3
        var list = List.of(aca(control(1), answer(50), false));
        var stats = ReportingCalculator.compute(list, 3);
        assertThat(stats.coveragePercent).isEqualTo(33.3);
    }
}
