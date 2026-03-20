package com.govinc.reporting;

import com.govinc.assessment.Assessment;
import com.govinc.assessment.AssessmentControlAnswer;
import com.govinc.assessment.AssessmentDetails;
import com.govinc.assessment.AssessmentDetailsService;
import com.govinc.assessment.AssessmentRepository;
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

    /** Top-level result returned to the controller/template. */
    public static class CalculationResult {
        public final CapabilityReport report;
        public final List<CapabilityScore> capabilityScores;
        public final int orgUnitsIncluded;
        public final int assessmentsIncluded;

        CalculationResult(CapabilityReport report,
                          List<CapabilityScore> capabilityScores,
                          int orgUnitsIncluded,
                          int assessmentsIncluded) {
            this.report = report;
            this.capabilityScores = capabilityScores;
            this.orgUnitsIncluded = orgUnitsIncluded;
            this.assessmentsIncluded = assessmentsIncluded;
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

        DomainScore(SecurityControlDomain domain,
                    ReportingCalculator.AssessmentStats stats,
                    int totalControls,
                    MaturityLevel maturityLevel) {
            this.domain = domain;
            this.score = stats.averageRating;
            this.coverage = stats.coveragePercent;
            this.answeredControls = stats.answeredControls;
            this.totalControls = totalControls;
            this.maturityLevel = maturityLevel;
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

        // 3. Gather the latest assessment per org unit (same catalog), then pool all answers
        List<Assessment> relevantAssessments = assessmentRepository.findAll().stream()
                .filter(a -> a.getOrgUnit() != null && orgUnitIds.contains(a.getOrgUnit().getId()))
                .filter(a -> catalog == null || (a.getSecurityCatalog() != null
                        && a.getSecurityCatalog().getId().equals(catalog.getId())))
                .collect(Collectors.toList());

        // keep only the most-recent assessment per org unit
        Map<Long, Assessment> latestByOrgUnit = new LinkedHashMap<>();
        for (Assessment a : relevantAssessments) {
            Long ouId = a.getOrgUnit().getId();
            Assessment existing = latestByOrgUnit.get(ouId);
            if (existing == null || (a.getCreationDate() != null && existing.getCreationDate() != null
                    && a.getCreationDate().isAfter(existing.getCreationDate()))) {
                latestByOrgUnit.put(ouId, a);
            }
        }

        List<AssessmentControlAnswer> allAnswers = new ArrayList<>();
        for (Assessment a : latestByOrgUnit.values()) {
            Optional<AssessmentDetails> detailsOpt = assessmentDetailsService.findById(a.getId());
            detailsOpt.ifPresent(d -> allAnswers.addAll(d.getControlAnswers()));
        }

        // 4. Deduplicate answers across the full pool
        Map<Long, AssessmentControlAnswer> effectiveAnswers = CapabilityCalculator.deduplicateAnswers(allAnswers);

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
                        CapabilityCalculator.computeForControls(effectiveAnswers, domainControlIds);
                domainScores.add(new DomainScore(
                    domain,
                    domainStats,
                    domainControlIds.size(),
                    nearestMaturityLevel(maturityModel, domainStats.averageRating)
                ));
            }

            // Sort domain scores by name for consistent display
            domainScores.sort(Comparator.comparing(
                    d -> d.domain.getName() != null ? d.domain.getName() : "",
                    String.CASE_INSENSITIVE_ORDER));

            ReportingCalculator.AssessmentStats capStats =
                    CapabilityCalculator.computeForControls(effectiveAnswers, capabilityControlIds);
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

        return new CalculationResult(report, capabilityScores, orgUnitIds.size(), latestByOrgUnit.size());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

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
