package com.govinc.assessment;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Self-healing consistency layer for {@link AssessmentDetails}.
 *
 * <p>An earlier defect in {@code AssessmentDetailsService.findById(Long)} silently
 * treated an Assessment id as an AssessmentDetails primary key whenever the two
 * happened to collide. That caused two classes of long-lived data corruption:</p>
 *
 * <ul>
 *   <li><b>Cross-linked details</b> — one {@code AssessmentDetails} row whose
 *       {@code assessments} M2M set contains more than one {@code Assessment}.
 *       Editing answers/comments on any of those assessments mutated the shared
 *       row, polluting the others.</li>
 *   <li><b>Duplicate details</b> — multiple {@code AssessmentDetails} rows that
 *       all point at the same assessment. Reads see only one of them; the others
 *       carry stale or partial answer data.</li>
 *   <li><b>Orphans</b> — rows whose {@code assessments} link set is empty.</li>
 * </ul>
 *
 * <p>This service detects and repairs all three cases. Repairs run:</p>
 * <ul>
 *   <li>Once at application start (gated by
 *       {@code govinc.assessment.consistency.repair-on-startup}, default
 *       {@code true}), via {@link #repairAll()}.</li>
 *   <li>Lazily on every read path that goes through
 *       {@link AssessmentDetailsService#findByAssessmentId(Long)}, via
 *       {@link #repairForAssessment(Long)}. The lazy path is cheap when no
 *       inconsistency is present.</li>
 * </ul>
 *
 * <p>The merge strategy for {@link AssessmentControlAnswer}s favours the most
 * informative entry: a non-null {@code maturityAnswer} wins over a null one;
 * with that tied, the longer {@code comment} wins; finally, the highest entity
 * id wins (a proxy for "most recently written"). This is conservative — it
 * never throws data away when a more authoritative entry is available.</p>
 */
@Service
public class AssessmentDetailsConsistencyService {

    private static final Logger log =
            LoggerFactory.getLogger(AssessmentDetailsConsistencyService.class);

    @Autowired
    private AssessmentDetailsRepository repository;

    @Autowired
    private AssessmentControlAnswerRepository answerRepository;

    /**
     * Used to obtain a proxied self-reference so that calling {@code repairAll()}
     * from the startup hook honours its {@code @Transactional} boundary (a
     * direct {@code this.repairAll()} would bypass the proxy and run inside
     * whatever transaction the caller is in — or none at all).
     */
    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private DataSource dataSource;

    /** Cached at startup: does the legacy {@code assessmentdetails_maturityanswer}
     *  join table exist in the current schema? When {@code false}, the defensive
     *  cleanup before {@code AssessmentDetails} deletion is skipped entirely so
     *  it cannot poison a transaction with a "table does not exist" SQLException. */
    private boolean legacyMaturityAnswerTableExists = false;

    @Value("${govinc.assessment.consistency.repair-on-startup:false}")
    private boolean repairOnStartup;

    @PostConstruct
    void detectLegacyTables() {
        legacyMaturityAnswerTableExists = tableExists("assessmentdetails_maturityanswer");
        if (legacyMaturityAnswerTableExists) {
            log.info("[consistency] detected legacy join table 'assessmentdetails_maturityanswer'; "
                    + "defensive cleanup will run before AssessmentDetails deletes");
        }
    }

    private boolean tableExists(String tableName) {
        try (Connection c = dataSource.getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            // Search both lowercase and uppercase identifiers to cover MariaDB/MySQL
            // (case-sensitive on some OSes) and H2 (uppercases unquoted identifiers).
            for (String name : new String[]{ tableName, tableName.toUpperCase() }) {
                try (ResultSet rs = md.getTables(null, null, name, new String[]{"TABLE"})) {
                    if (rs.next()) return true;
                }
            }
        } catch (SQLException e) {
            log.warn("[consistency] could not probe database metadata for legacy tables: {}",
                    e.getMessage());
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // Startup hook
    // ---------------------------------------------------------------------

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!repairOnStartup) {
            log.info("[consistency] startup repair disabled (set "
                    + "govinc.assessment.consistency.repair-on-startup=true to enable)");
            return;
        }
        try {
            // Call through the proxy so @Transactional on repairAll() applies.
            AssessmentDetailsConsistencyService self =
                    applicationContext.getBean(AssessmentDetailsConsistencyService.class);
            RepairSummary summary = self.repairAll();
            if (summary.totalActions() == 0) {
                log.info("[consistency] startup scan: no AssessmentDetails inconsistencies found");
            } else {
                log.warn("[consistency] startup scan repaired: {}", summary);
            }
        } catch (Exception e) {
            // Swallowed deliberately: a startup repair failure must never prevent
            // the application from coming up. The on-demand repair path still
            // handles inconsistencies lazily on every read.
            log.error("[consistency] startup repair failed (continuing)", e);
        }
    }

    // ---------------------------------------------------------------------
    // Bulk repair (startup or admin-triggered)
    // ---------------------------------------------------------------------

    /**
     * Scans the whole {@code assessment_details} table and repairs every kind
     * of inconsistency. Safe to call repeatedly; idempotent.
     */
    @Transactional
    public RepairSummary repairAll() {
        RepairSummary summary = new RepairSummary();

        // 1. Split cross-linked details first — this may create extra rows
        //    that themselves contribute to the duplicate-per-assessment case
        //    handled in step 2.
        for (AssessmentDetails ad : repository.findCrossLinked()) {
            summary.splitCrossLinks += splitCrossLinked(ad);
        }

        // 2. Merge duplicates per assessment.
        //    Build a fresh view of the assessment-link graph after the split.
        Map<Long, List<AssessmentDetails>> byAssessment = new LinkedHashMap<>();
        for (AssessmentDetails ad : repository.findAll()) {
            if (ad.getAssessments() == null) continue;
            for (Assessment a : ad.getAssessments()) {
                if (a == null || a.getId() == null) continue;
                byAssessment.computeIfAbsent(a.getId(), k -> new ArrayList<>()).add(ad);
            }
        }
        for (Map.Entry<Long, List<AssessmentDetails>> e : byAssessment.entrySet()) {
            if (e.getValue().size() > 1) {
                summary.mergedDuplicates += mergeDuplicates(e.getKey(), e.getValue());
            }
        }

        // 3. Delete orphans (no assessment link at all).
        for (AssessmentDetails ad : repository.findOrphans()) {
            log.warn("[consistency] deleting orphan AssessmentDetails id={} (no assessment link)",
                    ad.getId());
            deleteDetailsSafely(ad);
            summary.orphansDeleted++;
        }

        return summary;
    }

    // ---------------------------------------------------------------------
    // On-demand repair for a single assessment (cheap fast path)
    // ---------------------------------------------------------------------

    /**
     * Repairs the {@code AssessmentDetails} graph for one assessment and returns
     * the (now unique) details row for it, if any. Returns {@code Optional.empty()}
     * when the assessment has never had a details row.
     */
    @Transactional
    public Optional<AssessmentDetails> repairForAssessment(Long assessmentId) {
        if (assessmentId == null) return Optional.empty();
        List<AssessmentDetails> matches = repository.findAllForAssessmentId(assessmentId);
        if (matches.isEmpty()) return Optional.empty();

        // Fix cross-links on every match first.
        boolean splitHappened = false;
        for (AssessmentDetails ad : new ArrayList<>(matches)) {
            if (ad.getAssessments() != null && ad.getAssessments().size() > 1) {
                if (splitCrossLinked(ad) > 0) splitHappened = true;
            }
        }
        if (splitHappened) {
            // Reload after split — rows may have been reassigned.
            matches = repository.findAllForAssessmentId(assessmentId);
        }
        if (matches.size() > 1) {
            mergeDuplicates(assessmentId, matches);
            matches = repository.findAllForAssessmentId(assessmentId);
        }
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
    }

    // ---------------------------------------------------------------------
    // Cross-link split
    // ---------------------------------------------------------------------

    /**
     * If {@code ad} is linked to N>1 assessments, keep it attached to the
     * oldest one and deep-clone its control answers into a fresh
     * {@code AssessmentDetails} for each of the other N-1 assessments.
     *
     * @return the number of new {@code AssessmentDetails} rows created.
     */
    private int splitCrossLinked(AssessmentDetails ad) {
        if (ad.getAssessments() == null || ad.getAssessments().size() <= 1) return 0;

        List<Assessment> linked = new ArrayList<>(ad.getAssessments());
        linked.removeIf(a -> a == null || a.getId() == null);
        if (linked.size() <= 1) return 0;

        // "Oldest" = lowest id. The row stays attached to that assessment.
        linked.sort(Comparator.comparing(Assessment::getId));
        Assessment keeper = linked.get(0);
        List<Assessment> others = linked.subList(1, linked.size());

        int created = 0;
        for (Assessment other : others) {
            AssessmentDetails copy = new AssessmentDetails();
            Set<Assessment> single = new HashSet<>();
            single.add(other);
            copy.setAssessments(single);
            copy.setDate(ad.getDate() != null ? ad.getDate() : LocalDate.now());
            copy.setName(ad.getName());
            copy.setCompletedDate(ad.getCompletedDate());

            Set<AssessmentControlAnswer> clonedAnswers = new HashSet<>();
            if (ad.getControlAnswers() != null) {
                for (AssessmentControlAnswer src : ad.getControlAnswers()) {
                    AssessmentControlAnswer clone = new AssessmentControlAnswer(
                            src.getSecurityControl(),
                            src.getMaturityAnswer(),
                            src.getComment());
                    clone.setIsOverride(src.getIsOverride());
                    clone.setIsNotApplicable(src.getIsNotApplicable());
                    clonedAnswers.add(answerRepository.save(clone));
                }
            }
            copy.setControlAnswers(clonedAnswers);
            repository.save(copy);
            created++;
            log.warn("[consistency] split cross-linked AssessmentDetails id={} -> created new id={} for assessment id={}",
                    ad.getId(), copy.getId(), other.getId());
        }

        // Reduce the original to a single assessment link.
        Set<Assessment> only = new HashSet<>();
        only.add(keeper);
        ad.setAssessments(only);
        repository.save(ad);
        log.warn("[consistency] AssessmentDetails id={} now exclusively linked to assessment id={}",
                ad.getId(), keeper.getId());
        return created;
    }

    // ---------------------------------------------------------------------
    // Duplicate-per-assessment merge
    // ---------------------------------------------------------------------

    /**
     * Collapses {@code duplicates} (all linked to the same assessment id) down
     * to a single row, preserving the richest information per security control.
     *
     * @return the number of rows deleted.
     */
    private int mergeDuplicates(Long assessmentId, List<AssessmentDetails> duplicates) {
        if (duplicates == null || duplicates.size() <= 1) return 0;

        // Keep the oldest (lowest id) as the survivor.
        List<AssessmentDetails> sorted = new ArrayList<>(duplicates);
        sorted.sort(Comparator.comparing(AssessmentDetails::getId,
                Comparator.nullsLast(Comparator.naturalOrder())));
        AssessmentDetails survivor = sorted.get(0);
        List<AssessmentDetails> losers = sorted.subList(1, sorted.size());

        // Index survivor's answers by securityControl id for fast lookup.
        Map<Long, AssessmentControlAnswer> bySc = new LinkedHashMap<>();
        if (survivor.getControlAnswers() != null) {
            for (AssessmentControlAnswer a : survivor.getControlAnswers()) {
                if (a.getSecurityControl() != null && a.getSecurityControl().getId() != null) {
                    bySc.put(a.getSecurityControl().getId(), a);
                }
            }
        }

        int deleted = 0;
        for (AssessmentDetails loser : losers) {
            if (loser.getControlAnswers() != null) {
                for (AssessmentControlAnswer candidate : loser.getControlAnswers()) {
                    if (candidate.getSecurityControl() == null
                            || candidate.getSecurityControl().getId() == null) continue;
                    Long scId = candidate.getSecurityControl().getId();
                    AssessmentControlAnswer current = bySc.get(scId);
                    if (current == null) {
                        // Move the candidate over by cloning into a new row
                        // (the original belongs to the loser's cascade and will
                        // be deleted with it).
                        AssessmentControlAnswer moved = new AssessmentControlAnswer(
                                candidate.getSecurityControl(),
                                candidate.getMaturityAnswer(),
                                candidate.getComment());
                        moved.setIsOverride(candidate.getIsOverride());
                        moved.setIsNotApplicable(candidate.getIsNotApplicable());
                        moved = answerRepository.save(moved);
                        survivor.getControlAnswers().add(moved);
                        bySc.put(scId, moved);
                    } else if (isBetter(candidate, current)) {
                        // Promote candidate's content into the surviving row.
                        current.setMaturityAnswer(candidate.getMaturityAnswer());
                        current.setComment(candidate.getComment());
                        current.setIsOverride(candidate.getIsOverride());
                        current.setIsNotApplicable(candidate.getIsNotApplicable());
                        answerRepository.save(current);
                    }
                }
            }
            // Detach loser from the assessment to avoid FK retention, then delete.
            if (loser.getAssessments() != null) loser.getAssessments().clear();
            deleteDetailsSafely(loser);
            deleted++;
            log.warn("[consistency] merged duplicate AssessmentDetails id={} into id={} for assessment id={}",
                    loser.getId(), survivor.getId(), assessmentId);
        }
        repository.save(survivor);
        return deleted;
    }

    /**
     * Deletes an {@link AssessmentDetails} row, first clearing any rows from
     * the legacy {@code assessmentdetails_maturityanswer} join table whose
     * foreign key would otherwise block the delete. The legacy table is no
     * longer mapped by the entity model but persists in databases migrated
     * from an earlier schema. The cleanup is tolerant: if the table does not
     * exist the failure is logged and ignored, and the delete still proceeds.
     */
    private void deleteDetailsSafely(AssessmentDetails ad) {
        if (ad == null || ad.getId() == null) return;
        if (legacyMaturityAnswerTableExists) {
            try {
                int cleared = repository.deleteLegacyMaturityAnswerLinks(ad.getId());
                if (cleared > 0) {
                    log.warn("[consistency] removed {} legacy assessmentdetails_maturityanswer row(s) for details id={}",
                            cleared, ad.getId());
                }
            } catch (Exception e) {
                log.warn("[consistency] legacy maturity-answer cleanup failed for id={}: {}",
                        ad.getId(), e.getMessage());
            }
        }
        repository.delete(ad);
    }

    /**
     * Returns {@code true} when {@code candidate} carries strictly richer
     * information than {@code current} (used by the duplicate-merge to decide
     * which row's content to keep per security control).
     */
    private boolean isBetter(AssessmentControlAnswer candidate, AssessmentControlAnswer current) {
        boolean candHasAnswer = candidate.getMaturityAnswer() != null;
        boolean currHasAnswer = current.getMaturityAnswer() != null;
        if (candHasAnswer != currHasAnswer) return candHasAnswer;

        int candCommentLen = candidate.getComment() == null ? 0 : candidate.getComment().length();
        int currCommentLen = current.getComment() == null ? 0 : current.getComment().length();
        if (candCommentLen != currCommentLen) return candCommentLen > currCommentLen;

        Long candId = candidate.getId() == null ? Long.MIN_VALUE : candidate.getId();
        Long currId = current.getId() == null ? Long.MIN_VALUE : current.getId();
        return candId > currId;
    }

    // ---------------------------------------------------------------------
    // Result type
    // ---------------------------------------------------------------------

    public static final class RepairSummary {
        public int splitCrossLinks = 0;
        public int mergedDuplicates = 0;
        public int orphansDeleted = 0;

        public int totalActions() {
            return splitCrossLinks + mergedDuplicates + orphansDeleted;
        }

        @Override
        public String toString() {
            return "splitCrossLinks=" + splitCrossLinks
                    + ", mergedDuplicates=" + mergedDuplicates
                    + ", orphansDeleted=" + orphansDeleted;
        }
    }
}
