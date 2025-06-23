package com.govinc.compliance;

import com.govinc.catalog.SecurityCatalog;
import com.govinc.organization.OrgUnit;
import com.govinc.assessment.Assessment;
import com.govinc.assessment.AssessmentRepository;
import com.govinc.assessment.AssessmentDetails;
import com.govinc.assessment.AssessmentDetailsRepository;
import com.govinc.assessment.AssessmentControlAnswer;
import com.govinc.organization.OrgUnitService;
import com.govinc.maturity.MaturityAnswer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ComplianceService {
    @Autowired
    private ComplianceCheckRepository complianceCheckRepository;
    @Autowired
    private ComplianceThresholdRepository thresholdRepository;
    @Autowired
    private AssessmentRepository assessmentRepository;
    @Autowired
    private AssessmentDetailsRepository assessmentDetailsRepository;
    @Autowired
    private OrgUnitService orgUnitService;

    // Get all ComplianceChecks
    public List<ComplianceCheck> findAll() {
        return complianceCheckRepository.findAll();
    }

    public Optional<ComplianceCheck> findById(Long id) {
        return complianceCheckRepository.findById(id);
    }

    public ComplianceCheck save(ComplianceCheck check) {
        return complianceCheckRepository.save(check);
    }

    public void delete(Long id) {
        complianceCheckRepository.deleteById(id);
    }

    // Calculate compliance for orgUnit (and its children recursively) for a given
    // ComplianceCheck
    public ComplianceResult calculateCompliance(ComplianceCheck check, OrgUnit orgUnit) {
        // Calculate aggregate compliance result for org+all children:
        Map<OrgUnit, ComplianceResult> complianceResults = evaluateComplianceForOrgAndChildren(orgUnit, check,
                check.getSecurityCatalog());
        // Make AND of all compliant values (the org itself and all descendants)
        boolean aggregateCompliant = true;
        for (ComplianceResult cr : complianceResults.values()) {
            if (!cr.isCompliant()) {
                aggregateCompliant = false;
                break;
            }
        }
        // Report orgUnit's compliance thresholds details, but aggregate compliant is AND result
        ComplianceResult orgResult = complianceResults.get(orgUnit);
        if (orgResult == null) {
            return new ComplianceResult(aggregateCompliant, new HashMap<>(), 0);
        } else {
            // Copy the orgResult but with aggregate compliant field
            ComplianceResult agg = new ComplianceResult(aggregateCompliant, orgResult.getThresholdsDetails(), orgResult.getCheckedAssessments());
            agg.setControlsAnswered(orgResult.getControlsAnswered());
            agg.setControlsTotal(orgResult.getControlsTotal());
            agg.setCoveragePercent(orgResult.getCoveragePercent());
            agg.setAveragePercent(orgResult.getAveragePercent());
            return agg;
        }
    }

    // Helper: recursively get all OrgUnit descendants (including self)
    public List<OrgUnit> collectWithChildren(OrgUnit root) {
        List<OrgUnit> all = new ArrayList<>();
        collectWithChildrenRecursive(root, all);
        return all;
    }

    private void collectWithChildrenRecursive(OrgUnit parent, List<OrgUnit> list) {
        list.add(parent);
        if (parent.getChildren() != null) {
            for (OrgUnit child : parent.getChildren()) {
                collectWithChildrenRecursive(child, list);
            }
        }
    }

    // Finds AssessmentDetails for an assessmentId
    private AssessmentDetails findAssessmentDetailsForAssessment(Long assessmentId) {
        // Brute-force: scan all (for performance, better to index)
        for (AssessmentDetails details : assessmentDetailsRepository.findAll()) {
            for (Assessment ass : details.getAssessments()) {
                if (ass.getId().equals(assessmentId))
                    return details;
            }
        }
        return null;
    }

    // Evaluate one threshold for a set of control answers
    private boolean evaluateThreshold(ComplianceThreshold t, List<AssessmentControlAnswer> answers) {
        if ("ALL_ABOVE".equals(t.getType())) {
            for (AssessmentControlAnswer answer : answers) {
                MaturityAnswer ma = answer.getMaturityAnswer();
                if (ma == null || ma.getRating() < t.getValue()) {
                    return false;
                }
            }
            return true;
        } else if ("AVERAGE_ABOVE".equals(t.getType())) {
            int sum = 0;
            int count = 0;
            for (AssessmentControlAnswer answer : answers) {
                MaturityAnswer ma = answer.getMaturityAnswer();
                if (ma != null) {
                    sum += ma.getRating();
                    count++;
                }
            }
            if (count == 0)
                return false;
            return (sum / (double) count) >= t.getValue();
        }
        return false;
    }

    // Data class for compliance result
    public static class ComplianceResult {
        private boolean compliant;
        private Map<String, Object> thresholdsDetails;
        private int checkedAssessments;
        // Added for coverage reporting
        private int controlsAnswered;
        private int controlsTotal;
        // Added for UI: coveragePercent and averagePercent
        private double coveragePercent;
        private double averagePercent;

        public ComplianceResult(boolean compliant, Map<String, Object> thresholdsDetails, int checkedAssessments) {
            this.compliant = compliant;
            this.thresholdsDetails = (thresholdsDetails != null) ? thresholdsDetails : new HashMap<>();
            this.checkedAssessments = checkedAssessments;
        }

        // For coverage
        public void setControlsAnswered(int covered) {
            this.controlsAnswered = covered;
        }

        public int getControlsAnswered() {
            return controlsAnswered;
        }

        public void setControlsTotal(int total) {
            this.controlsTotal = total;
        }

        public int getControlsTotal() {
            return controlsTotal;
        }

        public boolean isCompliant() {
            return compliant;
        }

        public Map<String, Object> getThresholdsDetails() {
            return thresholdsDetails;
        }

        public int getCheckedAssessments() {
            return checkedAssessments;
        }

        public double getCoveragePercent() {
            return coveragePercent;
        }

        public void setCoveragePercent(double coveragePercent) {
            this.coveragePercent = coveragePercent;
        }

        public double getAveragePercent() {
            return averagePercent;
        }

        public void setAveragePercent(double averagePercent) {
            this.averagePercent = averagePercent;
        }
    }

    // Store latest totals for controller access (not thread safe, but works for
    // single request)
    private double latestTotalCoveragePercent = 0.0;
    private double latestTotalAveragePercent = 0.0;

    public double getLatestTotalCoveragePercent() {
        return latestTotalCoveragePercent;
    }

    public double getLatestTotalAveragePercent() {
        return latestTotalAveragePercent;
    }

    // Find the latest Assessment for each OrgUnit for the specified catalog
    public Map<Long, Assessment> getLatestAssessments(List<OrgUnit> orgUnits, SecurityCatalog catalog) {
        Map<Long, Assessment> latest = new HashMap<>();
        List<Long> unitIds = new ArrayList<>();
        for (OrgUnit u : orgUnits)
            unitIds.add(u.getId());
        // Only keep the most recent assessment per org unit
        for (Assessment a : assessmentRepository.findAll()) {
            if (a.getOrgUnit() != null && a.getSecurityCatalog() != null
                    && a.getSecurityCatalog().getId().equals(catalog.getId())
                    && unitIds.contains(a.getOrgUnit().getId())) {
                Assessment prev = latest.get(a.getOrgUnit().getId());
                if (prev == null ||
                        (a.getDate() != null && (prev.getDate() == null || a.getDate().isAfter(prev.getDate())))) {
                    latest.put(a.getOrgUnit().getId(), a);
                }
            }
        }
        // Remove less-recent duplicates if any, only latest remains per unit

        return latest;
    }

    // Evaluate compliance and coverage for all org units
    public Map<OrgUnit, ComplianceResult> evaluateComplianceForOrgAndChildren(
            OrgUnit root, ComplianceCheck check, SecurityCatalog catalog) {
        // For the flat list UI: include the org unit itself along with its children
        // (each unit should have a row)
        // List must include root and all children (recursive, i.e., children of
        // children)
        List<OrgUnit> flatUnits = collectWithChildren(root);
        Map<Long, Assessment> latestAssessments = getLatestAssessments(flatUnits, catalog);
        Set<Long> controlIds = new HashSet<>();
        if (catalog.getSecurityControls() != null) {
            for (var ctrl : catalog.getSecurityControls())
                controlIds.add(ctrl.getId());
        }
        int totalControls = controlIds.size();
        Map<OrgUnit, ComplianceResult> resultMap = new LinkedHashMap<>();
        // Aggregate variables for total row
        int totalControlsAnswered = 0;
        int totalControlsPossible = 0;
        double totalScoreSum = 0.0;
        int totalScoreCount = 0;

        // Map to hold compliance status for each org unit for parent-child propagation
        Map<OrgUnit, Boolean> childComplianceMap = new HashMap<>();
        // Map to hold score % for all
        Map<OrgUnit, Double> childAveragePercentMap = new HashMap<>();

        // Strictly construct the UI list: only direct assessments for each unit
        for (OrgUnit unit : flatUnits) {
            // Only use direct assessments for this org unit (no aggregation, no
            // propagation, only the org unit's own latest assessment)
            Assessment a = latestAssessments.get(unit.getId());
            int covered = 0;
            AssessmentDetails details = (a != null) ? findAssessmentDetailsForAssessment(a.getId()) : null;
            Set<Long> answered = new HashSet<>();
            double scoreSum = 0.0;
            int scoreCount = 0;
            if (details != null && details.getControlAnswers() != null) {
                for (AssessmentControlAnswer answer : details.getControlAnswers()) {
                    if (answer.getSecurityControl() != null
                            && controlIds.contains(answer.getSecurityControl().getId())) {
                        answered.add(answer.getSecurityControl().getId());
                        scoreSum += answer.getScore();
                        scoreCount++;
                    }
                }
                covered = answered.size();
            }
            // Use only direct results - do not aggregate or propagate
            List<AssessmentControlAnswer> controlAnswers = new ArrayList<>();
            if (details != null && details.getControlAnswers() != null) {
                controlAnswers.addAll(details.getControlAnswers());
            }

            // Even if no assessment: treat as 0 answered, 0 scored, but total possible
            // controls still applies
            totalControlsAnswered += covered;
            totalControlsPossible += totalControls;
            totalScoreSum += scoreSum;
            totalScoreCount += scoreCount;

            // Evaluate thresholds for this org unit, using only its own assessment
            boolean compliant = true;
            Map<String, Object> thresholdDetails = new HashMap<>();
            if (covered == 0) {
                compliant = false;
            } else if (check.getThresholds() != null) {
                for (ComplianceThreshold t : check.getThresholds()) {
                    boolean passed = evaluateThreshold(t, controlAnswers);
                    thresholdDetails.put(t.getRuleDescription() + " [" + t.getType() + " " + t.getValue() + "%]",
                            passed);
                    if (!passed)
                        compliant = false;
                }
            }

            double coveragePercent = (totalControls == 0) ? 0.0 : ((double) covered * 100.0) / (double) totalControls;
            double averagePercent = (scoreCount == 0) ? 0.0 : scoreSum / (double) scoreCount;

            ComplianceResult result = new ComplianceResult(compliant, thresholdDetails, 1);
            result.setControlsAnswered(covered);
            result.setControlsTotal(totalControls);
            result.setCoveragePercent(round(coveragePercent, 2));
            result.setAveragePercent(round(averagePercent, 2));

            // Add a dedicated status string for UI to use in status column
            String statusString = compliant ? "Compliant" : "Non-compliant";
            //result.thresholdsDetails.put("status", statusString);

            childComplianceMap.put(unit, compliant);
            childAveragePercentMap.put(unit, averagePercent);

            resultMap.put(unit, result);
        }
        // --- END: UI list with only direct compliance/results per unit ---

        // If you need to calculate propagated/rollup compliance, do it here (do not change resultMap results).
        // The resultMap for table/list always shows each org unit's direct compliance only.

        double avgSum = 0.0;
        int avgCount = 0;
        List<OrgUnit> allUnits = collectWithChildren(root);
        for (OrgUnit unit : allUnits) {
            avgSum += childAveragePercentMap.getOrDefault(unit, 0.0);
            avgCount++;
        }
        double totalCoveragePercent = (totalControlsPossible == 0) ? 0.0
                : ((double) totalControlsAnswered * 100.0) / (double) totalControlsPossible;
        double totalAveragePercent = (avgCount == 0) ? 0.0 : avgSum / avgCount;
        totalCoveragePercent = round(totalCoveragePercent, 2);
        totalAveragePercent = round(totalAveragePercent, 2);
        this.latestTotalCoveragePercent = totalCoveragePercent;
        this.latestTotalAveragePercent = totalAveragePercent;
        return resultMap;
    }

    // Utility (round double to x decimals)
    private double round(double value, int places) {
        if (places < 0)
            throw new IllegalArgumentException();
        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }

}
