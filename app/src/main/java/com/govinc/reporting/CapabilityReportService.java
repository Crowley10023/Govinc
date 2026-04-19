package com.govinc.reporting;

import com.govinc.assessment.Assessment;
import com.govinc.assessment.AssessmentControlAnswer;
import com.govinc.assessment.AssessmentDetails;
import com.govinc.assessment.AssessmentDetailsService;
import com.govinc.assessment.AssessmentRepository;
import com.govinc.assessment.AssessmentStatus;
import com.govinc.catalog.SecurityCapability;
import com.govinc.catalog.SecurityCatalog;
import com.govinc.catalog.SecurityControl;
import com.govinc.catalog.SecurityControlDomain;
import com.govinc.maturity.MaturityAnswer;
import com.govinc.maturity.MaturityModel;
import com.govinc.organization.OrgUnit;
import com.govinc.organization.OrgUnitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CapabilityReportService {

    @Autowired
    private CapabilityReportRepository repository;

    @Autowired
    private AssessmentRepository assessmentRepository;

    @Autowired
    private AssessmentDetailsService assessmentDetailsService;

    @Autowired
    private OrgUnitService orgUnitService;

    // ─── CRUD ────────────────────────────────────────────────────────────────

    public List<CapabilityReport> findAll() { return repository.findAll(); }

    public Optional<CapabilityReport> findById(Long id) { return repository.findById(id); }

    public CapabilityReport save(CapabilityReport report) { return repository.save(report); }

    public void deleteById(Long id) { repository.deleteById(id); }

    // ─── Calculation ─────────────────────────────────────────────────────────

    /** Summary info about an assessment included in this calculation. */
    public static class AssessmentInfo {
        public final Long id;
        public final String name;
        public final String orgUnitName;
        public final LocalDate date;
        public final String status;

        AssessmentInfo(Assessment a) {
            this.id = a.getId();
            this.name = a.getName() != null ? a.getName() : "Assessment #" + a.getId();
            this.orgUnitName = a.getOrgUnit() != null && a.getOrgUnit().getName() != null
                    ? a.getOrgUnit().getName() : "";
            this.date = a.getCloseDate() != null ? a.getCloseDate() : a.getCreationDate();
            this.status = a.getStatus() != null ? a.getStatus().name() : "";
        }
    }

    /** Score for a single security control within a domain (averaged across all assessments). */
    public static class ControlScore {
        public final String name;
        public final double score;
        public final boolean answered;

        ControlScore(String name, double score, boolean answered) {
            this.name = name;
            this.score = score;
            this.answered = answered;
        }
    }

    /** Top-level result returned to the controller/template. */
    public static class CalculationResult {
        public final CapabilityReport report;
        public final List<CapabilityScore> capabilityScores;
        public final int orgUnitsIncluded;
        public final int assessmentsIncluded;
        public final List<AssessmentInfo> usedAssessments;

        CalculationResult(CapabilityReport report,
                          List<CapabilityScore> capabilityScores,
                          int orgUnitsIncluded,
                          List<AssessmentInfo> usedAssessments) {
            this.report = report;
            this.capabilityScores = capabilityScores;
            this.orgUnitsIncluded = orgUnitsIncluded;
            this.assessmentsIncluded = usedAssessments.size();
            this.usedAssessments = usedAssessments;
        }
    }

    public static class CapabilityScore {
        public final SecurityCapability capability;
        public final double score;
        public final double coverage;
        public final int answeredControls;
        public final int totalControls;
        public final List<DomainScore> domainScores;
        public final MaturityLevel maturityLevel;

        CapabilityScore(SecurityCapability capability,
                        ReportingCalculator.AssessmentStats stats,
                        List<DomainScore> domainScores,
                        MaturityLevel maturityLevel) {
            this.capability = capability;
            this.score = stats.averageRating;
            this.coverage = stats.coveragePercent;
            this.answeredControls = stats.answeredControls;
            this.totalControls = domainScores.stream().mapToInt(d -> d.totalControls).sum();
            this.domainScores = domainScores;
            this.maturityLevel = maturityLevel;
        }
    }

    public static class DomainScore {
        public final SecurityControlDomain domain;
        public final double score;
        public final double coverage;
        public final int answeredControls;
        public final int totalControls;
        public final MaturityLevel maturityLevel;
        public final List<ControlScore> controlScores;

        DomainScore(SecurityControlDomain domain,
                    ReportingCalculator.AssessmentStats stats,
                    int totalControls,
                    MaturityLevel maturityLevel,
                    List<ControlScore> controlScores) {
            this.domain = domain;
            this.score = stats.averageRating;
            this.coverage = stats.coveragePercent;
            this.answeredControls = stats.answeredControls;
            this.totalControls = totalControls;
            this.maturityLevel = maturityLevel;
            this.controlScores = controlScores;
        }
    }

    public static class MaturityLevel {
        public final String answer;
        public final int rating;
        public final String tone;

        MaturityLevel(String answer, int rating, String tone) {
            this.answer = answer;
            this.rating = rating;
            this.tone = tone;
        }
    }

    /**
     * Calculates scores for all capabilities in the given report.
     * Scope: the report's orgUnit and all its recursive children.
     * Control filter: only controls that are in the report's related catalog
     * AND belong to domains mapped to the capabilities.
     */
    public CalculationResult calculate(Long reportId) {
        CapabilityReport report = repository.findById(reportId)
                .orElseThrow(() -> new NoSuchElementException("Capability report not found: " + reportId));

        // 1. Collect all org unit IDs in scope
        Set<Long> orgUnitIds = collectOrgUnitIds(report.getOrgUnit());

        // 2. Find catalog to constrain control selection
        SecurityCatalog catalog = report.getSecurityCatalog();
        Set<Long> catalogControlIds = catalog != null
                ? catalog.getSecurityControls().stream()
                        .map(SecurityControl::getId)
                        .collect(Collectors.toSet())
                : null; // null means "no catalog filter"

        // 3. For each org unit pick the single best assessment:
        //    - effective date (closeDate for CLOSED, else creationDate) is compared first
        //    - tiebreaker: more answered controls wins
        List<Assessment> relevantAssessments = assessmentRepository.findAll().stream()
                .filter(a -> a.getOrgUnit() != null && orgUnitIds.contains(a.getOrgUnit().getId()))
                .filter(a -> catalog == null || (a.getSecurityCatalog() != null
                        && a.getSecurityCatalog().getId().equals(catalog.getId())))
                .collect(Collectors.toList());

        List<Long> relevantIds = relevantAssessments.stream()
                .map(Assessment::getId).collect(Collectors.toList());
        Map<Long, AssessmentDetails> detailsById =
                assessmentDetailsService.findAllByAssessmentIds(relevantIds);

        Map<Long, Assessment> latestByOrgUnit = new LinkedHashMap<>();
        for (Assessment a : relevantAssessments) {
            Long ouId = a.getOrgUnit().getId();
            Assessment existing = latestByOrgUnit.get(ouId);
            LocalDate candidateEff = effectiveDate(a);
            LocalDate existingEff  = existing == null ? null : effectiveDate(existing);
            int cmp;
            if (existing == null) {
                cmp = 1;
            } else if (candidateEff == null) {
                cmp = -1;
            } else if (existingEff == null) {
                cmp = 1;
            } else {
                cmp = candidateEff.compareTo(existingEff);
            }
            if (cmp > 0) {
                latestByOrgUnit.put(ouId, a);
            } else if (cmp == 0) {
                int candidateCount = detailsById.containsKey(a.getId())
                        ? detailsById.get(a.getId()).getControlAnswers().size() : 0;
                int existingCount  = detailsById.containsKey(existing.getId())
                        ? detailsById.get(existing.getId()).getControlAnswers().size() : 0;
                if (candidateCount > existingCount) {
                    latestByOrgUnit.put(ouId, a);
                }
            }
        }

        // 4. Pool answers from the one selected assessment per org unit,
        //    then average per control across all org units.
        List<AssessmentControlAnswer> allAnswers = new ArrayList<>();
        for (Assessment a : latestByOrgUnit.values()) {
            AssessmentDetails d = detailsById.get(a.getId());
            if (d == null) continue;
            // For CLOSED assessments that have a snapshot, only include answers
            // for controls that were frozen in that snapshot.
            Set<Long> snapshotIds = null;
            if (a.getStatus() == AssessmentStatus.CLOSED
                    && a.getSnapshotControls() != null
                    && !a.getSnapshotControls().isEmpty()) {
                snapshotIds = a.getSnapshotControls().stream()
                        .map(SecurityControl::getId)
                        .collect(Collectors.toSet());
            }
            final Set<Long> finalSnapshotIds = snapshotIds;
            for (AssessmentControlAnswer aca : d.getControlAnswers()) {
                if (finalSnapshotIds == null
                        || aca.getSecurityControl() == null
                        || finalSnapshotIds.contains(aca.getSecurityControl().getId())) {
                    allAnswers.add(aca);
                }
            }
        }

        // 5. Average scores per control across all selected assessments
        Map<Long, Double> averagedScores = CapabilityCalculator.averageScoresPerControl(allAnswers);

        // 5. Score each capability
        MaturityModel maturityModel = report.getMaturityModel();
        List<CapabilityScore> capabilityScores = new ArrayList<>();
        for (SecurityCapability capability : report.getCapabilities()) {
            List<DomainScore> domainScores = new ArrayList<>();
            Set<Long> capabilityControlIds = new HashSet<>();

            for (SecurityControlDomain domain : capability.getDomains()) {
                Set<Long> domainControlIds = domain.getSecurityControls().stream()
                        .map(SecurityControl::getId)
                        .filter(cid -> catalogControlIds == null || catalogControlIds.contains(cid))
                        .collect(Collectors.toSet());

                capabilityControlIds.addAll(domainControlIds);

                ReportingCalculator.AssessmentStats domainStats =
                        CapabilityCalculator.computeForControlsAveraged(averagedScores, domainControlIds);

                // Build per-control scores for the domain popup
                List<ControlScore> controlScores = new ArrayList<>();
                for (SecurityControl ctrl : domain.getSecurityControls()) {
                    if (catalogControlIds != null && !catalogControlIds.contains(ctrl.getId())) continue;
                    Double avg = averagedScores.get(ctrl.getId());
                    controlScores.add(new ControlScore(
                            ctrl.getName() != null ? ctrl.getName() : "Control #" + ctrl.getId(),
                            avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0,
                            avg != null));
                }
                controlScores.sort(Comparator.comparing(
                        c -> c.name != null ? c.name : "", String.CASE_INSENSITIVE_ORDER));

                domainScores.add(new DomainScore(
                    domain,
                    domainStats,
                    domainControlIds.size(),
                    nearestMaturityLevel(maturityModel, domainStats.averageRating),
                    controlScores
                ));
            }

            // Sort domain scores by name for consistent display
            domainScores.sort(Comparator.comparing(
                    d -> d.domain.getName() != null ? d.domain.getName() : "",
                    String.CASE_INSENSITIVE_ORDER));

            ReportingCalculator.AssessmentStats capStats =
                    CapabilityCalculator.computeForControlsAveraged(averagedScores, capabilityControlIds);
                capabilityScores.add(new CapabilityScore(
                    capability,
                    capStats,
                    domainScores,
                    nearestMaturityLevel(maturityModel, capStats.averageRating)
                ));
        }

        capabilityScores.sort(Comparator.comparing(
                cs -> cs.capability.getName() != null ? cs.capability.getName() : "",
                String.CASE_INSENSITIVE_ORDER));

        List<AssessmentInfo> usedAssessments = new ArrayList<>();
        for (Assessment a : latestByOrgUnit.values()) {
            usedAssessments.add(new AssessmentInfo(a));
        }
        usedAssessments.sort(Comparator.comparing(
                ai -> ai.orgUnitName != null ? ai.orgUnitName : "", String.CASE_INSENSITIVE_ORDER));

        return new CalculationResult(report, capabilityScores, orgUnitIds.size(), usedAssessments);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Effective date used to determine which of two assessments is "later".
     *  CLOSED assessments are authoritative at their closeDate (snapshot point);
     *  all others are ordered by creationDate. */
    private static LocalDate effectiveDate(Assessment a) {
        return (a.getCloseDate() != null) ? a.getCloseDate() : a.getCreationDate();
    }

    private Set<Long> collectOrgUnitIds(OrgUnit root) {
        Set<Long> ids = new HashSet<>();
        if (root == null) return ids;
        OrgUnit hydrated = orgUnitService.getOrgUnitWithChildrenRecursive(root.getId()).orElse(root);
        addOrgUnitIdsRecursively(hydrated, ids);
        return ids;
    }

    private void addOrgUnitIdsRecursively(OrgUnit unit, Set<Long> ids) {
        if (unit == null || unit.getId() == null || !ids.add(unit.getId())) return;
        if (unit.getChildren() != null) {
            unit.getChildren().forEach(child -> addOrgUnitIdsRecursively(child, ids));
        }
    }

    private MaturityLevel nearestMaturityLevel(MaturityModel maturityModel, double score) {
        if (maturityModel == null || maturityModel.getMaturityAnswers() == null || maturityModel.getMaturityAnswers().isEmpty()) {
            return null;
        }

        MaturityAnswer nearest = maturityModel.getMaturityAnswers().stream()
                .min(Comparator
                        .comparingDouble((MaturityAnswer a) -> Math.abs(a.getRating() - score))
                        .thenComparingInt(MaturityAnswer::getRating)
                )
                .orElse(null);

        if (nearest == null) {
            return null;
        }

        int rating = nearest.getRating();
        String tone = rating >= 70 ? "high" : (rating >= 40 ? "mid" : "low");
        return new MaturityLevel(nearest.getAnswer(), rating, tone);
    }
}
