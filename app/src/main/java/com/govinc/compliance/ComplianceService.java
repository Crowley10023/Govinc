package com.govinc.compliance;

import com.govinc.catalog.SecurityCatalog;
import com.govinc.organization.OrgUnit;
import com.govinc.assessment.Assessment;
import com.govinc.assessment.AssessmentRepository;
import com.govinc.assessment.AssessmentDetails;
import com.govinc.assessment.AssessmentDetailsRepository;
import com.govinc.assessment.AssessmentDetailsService;
import com.govinc.assessment.AssessmentControlAnswer;
import com.govinc.organization.OrgUnitService;
import com.govinc.maturity.MaturityAnswer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

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
    private AssessmentDetailsService assessmentDetailsService;
    @Autowired
    private OrgUnitService orgUnitService;

    // Get all ComplianceChecks
    public List<ComplianceCheck> findAll() { return complianceCheckRepository.findAll(); }

    public Optional<ComplianceCheck> findById(Long id) { return complianceCheckRepository.findById(id); }

    public ComplianceCheck save(ComplianceCheck check) { return complianceCheckRepository.save(check); }

    public void delete(Long id) { complianceCheckRepository.deleteById(id); }

    // Data class for compliance result
    public static class ComplianceResult {
        private boolean compliant;
        private Map<String, Object> thresholdsDetails;
        private int checkedAssessments;
        // coverage reporting
        private int controlsAnswered;
        private int controlsTotal;
        private double coveragePercent;
        private double averagePercent;
        // debug/details
        private Map<String,Object> calculationDetails;
        private String calculationSummary;

        public ComplianceResult(boolean compliant, Map<String, Object> thresholdsDetails, int checkedAssessments) {
            this.compliant = compliant;
            this.thresholdsDetails = (thresholdsDetails != null) ? thresholdsDetails : new HashMap<>();
            this.checkedAssessments = checkedAssessments;
        }

        public boolean isCompliant() { return compliant; }
        public Map<String,Object> getThresholdsDetails() { return thresholdsDetails; }
        public int getCheckedAssessments() { return checkedAssessments; }

        public void setControlsAnswered(int covered) { this.controlsAnswered = covered; }
        public int getControlsAnswered() { return controlsAnswered; }
        public void setControlsTotal(int total) { this.controlsTotal = total; }
        public int getControlsTotal() { return controlsTotal; }

        public double getCoveragePercent() { return coveragePercent; }
        public void setCoveragePercent(double coveragePercent) { this.coveragePercent = coveragePercent; }

        public double getAveragePercent() { return averagePercent; }
        public void setAveragePercent(double averagePercent) { this.averagePercent = averagePercent; }

        public Map<String,Object> getCalculationDetails() { return calculationDetails; }
        public void setCalculationDetails(Map<String,Object> calculationDetails) { this.calculationDetails = calculationDetails; }
        public String getCalculationSummary() { return calculationSummary; }
        public void setCalculationSummary(String calculationSummary) { this.calculationSummary = calculationSummary; }
    }

    // Store latest totals for controller access (not thread safe, but works for single request)
    private double latestTotalCoveragePercent = 0.0;
    private double latestTotalAveragePercent = 0.0;
    private int latestTotalAssessmentsCount = 0;

    public double getLatestTotalCoveragePercent() { return latestTotalCoveragePercent; }
    public double getLatestTotalAveragePercent() { return latestTotalAveragePercent; }
    public int getLatestTotalAssessmentsCount() { return latestTotalAssessmentsCount; }

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Find the latest Assessment for each OrgUnit for the specified catalog
    public Map<Long, Assessment> getLatestAssessments(List<OrgUnit> orgUnits, SecurityCatalog catalog) {
        Map<Long, Assessment> latest = new HashMap<>();
        if (catalog == null) {
            System.out.println("ComplianceService.getLatestAssessments: catalog is null");
            return latest;
        }
        Set<Long> unitIds = new HashSet<>();
        for (OrgUnit u : orgUnits) unitIds.add(u.getId());

        for (Assessment a : assessmentRepository.findAll()) {
            if (a.getOrgUnit() == null || a.getSecurityCatalog() == null) continue;
            Long aUnitId = a.getOrgUnit().getId();
            Long aCatalogId = a.getSecurityCatalog().getId();
            if (!aCatalogId.equals(catalog.getId())) continue;
            if (!unitIds.contains(aUnitId)) continue;

            Assessment prev = latest.get(aUnitId);
            if (prev == null || (a.getCreationDate() != null && (prev.getCreationDate() == null || a.getCreationDate().isAfter(prev.getCreationDate())))) {
                System.out.println(String.format("Selecting assessment %s as latest for unit %s (prev=%s)", a.getId(), aUnitId, prev != null ? prev.getId() : null));
                latest.put(aUnitId, a);
            }
        }
        return latest;
    }

    // Finds the AssessmentDetails for an assessmentId using the service (which handles Assessment ID lookup)
    private AssessmentDetails findAssessmentDetailsForAssessment(Long assessmentId) {
        if (assessmentId == null) return null;
        
        System.out.println("DEBUG: Looking for AssessmentDetails for assessment id=" + assessmentId);
        
        // Use the service's findById which now handles Assessment ID lookup
        Optional<AssessmentDetails> detailsOpt = assessmentDetailsService.findById(assessmentId);
        
        if (detailsOpt.isPresent()) {
            AssessmentDetails details = detailsOpt.get();
            System.out.println("DEBUG: Found AssessmentDetails id=" + details.getId() + ", controlAnswers size=" + (details.getControlAnswers() != null ? details.getControlAnswers().size() : "null"));
            
            // Force eager loading into a new HashSet to ensure data persists after session closes
            if (details.getControlAnswers() != null) {
                Set<AssessmentControlAnswer> eagerLoadedAnswers = new HashSet<AssessmentControlAnswer>(details.getControlAnswers());
                details.setControlAnswers(eagerLoadedAnswers);
                System.out.println("DEBUG: After eager load, controlAnswers size=" + details.getControlAnswers().size());
            }
            
            return details;
        }
        
        System.out.println("DEBUG: No AssessmentDetails found for assessment id=" + assessmentId);
        return null;
    }

    // Evaluate one threshold for a set of control answers
    private boolean evaluateThreshold(ComplianceThreshold t, List<AssessmentControlAnswer> answers) {
        if (t == null) return false;
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
            if (count == 0) return false;
            return (sum / (double) count) >= t.getValue();
        }
        return false;
    }

    // Evaluate compliance and coverage for all org units
    public Map<OrgUnit, ComplianceResult> evaluateComplianceForOrgAndChildren(OrgUnit root, ComplianceCheck check, SecurityCatalog catalog) {
        if (check == null) System.out.println("evaluateComplianceForOrgAndChildren called with null check for root=" + (root != null ? root.getId() : null));
        if (catalog == null) System.out.println("evaluateComplianceForOrgAndChildren called with null catalog for check=" + (check != null ? check.getId() : null));

        List<OrgUnit> flatUnits = collectWithChildren(root);
        // prepare ids
        Set<Long> unitIds = new HashSet<>();
        for (OrgUnit u : flatUnits) unitIds.add(u.getId());

        // latest assessments per unit for this catalog
        Map<Long, Assessment> latestAssessments = getLatestAssessments(flatUnits, catalog);

        // count how many assessments exist per unit for this catalog
        Map<Long,Integer> assessmentsCount = new HashMap<>();
        for (Assessment a : assessmentRepository.findAll()) {
            if (a.getOrgUnit() == null || a.getSecurityCatalog() == null) continue;
            if (!a.getSecurityCatalog().getId().equals(catalog.getId())) continue;
            if (!unitIds.contains(a.getOrgUnit().getId())) continue;
            assessmentsCount.put(a.getOrgUnit().getId(), assessmentsCount.getOrDefault(a.getOrgUnit().getId(), 0) + 1);
        }

        // controls in catalog
        Set<Long> controlIds = new HashSet<>();
        if (catalog.getSecurityControls() != null) {
            for (var ctrl : catalog.getSecurityControls()) controlIds.add(ctrl.getId());
        }
        int totalControls = controlIds.size();
        System.out.println(String.format("Using catalog id=%s with %d controls for evaluateCompliance for root=%s",
                (catalog != null ? String.valueOf(catalog.getId()) : "null"), totalControls, root != null ? String.valueOf(root.getId()) : "null"));

        Map<OrgUnit, ComplianceResult> resultMap = new LinkedHashMap<>();

        // Totals
        int totalControlsAnswered = 0;
        int totalControlsPossible = 0;
        double totalAvgSum = 0.0;
        int totalAvgCount = 0;
        int totalAssessmentsCount = 0;

        for (OrgUnit unit : flatUnits) {
            Assessment a = latestAssessments.get(unit.getId());
            if (a != null) {
                // re-fetch latest assessment to ensure fresh DB-managed entity
                a = assessmentRepository.findById(a.getId()).orElse(a);
            }
            System.out.println(String.format("Unit %s: latest assessment id=%s", unit.getId(), a != null ? String.valueOf(a.getId()) : "null"));
            if (a != null) {
                System.out.println("  assessment.securityCatalogId=" + (a.getSecurityCatalog() != null ? a.getSecurityCatalog().getId() : null));
            }

            AssessmentDetails details = (a != null) ? findAssessmentDetailsForAssessment(a.getId()) : null;
            Set<Long> answeredControls = new HashSet<>();
            List<Long> answeredControlList = new ArrayList<>();
            double scoreSum = 0.0;
            int scoreCount = 0;

            int totalAnswersFound = 0; // number of answers anywhere in details
            int catalogAnswersFound = 0; // number of answers that belong to selected catalog
            if (details != null && details.getControlAnswers() != null) {
                for (AssessmentControlAnswer ans : details.getControlAnswers()) {
                    totalAnswersFound++;
                    if (ans.getSecurityControl() == null) continue;
                    Long ctrlId = ans.getSecurityControl().getId();
                    // Determine if this control is part of the selected catalog
                    boolean inCatalog = false;
                    if (controlIds.contains(ctrlId)) {
                        inCatalog = true;
                    } else if (ans.getSecurityControl().getSecurityCatalogs() != null) {
                        for (SecurityCatalog sc : ans.getSecurityControl().getSecurityCatalogs()) {
                            if (sc != null && sc.getId() != null && catalog != null && sc.getId().equals(catalog.getId())) {
                                inCatalog = true;
                                break;
                            }
                        }
                    }
                    if (!inCatalog) continue;
                    catalogAnswersFound++;
                    // record answered control (with rating if available)
                    answeredControls.add(ctrlId);
                    answeredControlList.add(ctrlId);
                    // Only add to score if maturityAnswer exists
                    if (ans.getMaturityAnswer() != null) {
                        scoreSum += ans.getScore();
                        scoreCount++;
                    }
                }
            }
            System.out.println(String.format("Unit %s: detailsTotalAnswers=%d (answered overall), catalogAnswers=%d (answered in catalog), answeredWithRating=%d, answeredList=%s", unit.getId(), totalAnswersFound, catalogAnswersFound, answeredControls.size(), answeredControlList));

            int covered = answeredControls.size();
            double coveragePercent = (totalControls == 0) ? 0.0 : ((double) covered * 100.0) / (double) totalControls;
            double averagePercent = (scoreCount == 0) ? 0.0 : scoreSum / (double) scoreCount;

            int checked = assessmentsCount.getOrDefault(unit.getId(), 0);

            boolean compliant = true;
            Map<String,Object> thresholdDetails = new HashMap<>();
            if (check != null && check.getThresholds() != null) {
                for (ComplianceThreshold t : check.getThresholds()) {
                    boolean passed = false;
                    if (covered != 0) {
                        // prepare control answers limited to catalog controls and to details
                        List<AssessmentControlAnswer> controlAnswers = new ArrayList<>();
                        if (details != null && details.getControlAnswers() != null) {
                            for (AssessmentControlAnswer ca : details.getControlAnswers()) {
                                if (ca.getSecurityControl() != null && controlIds.contains(ca.getSecurityControl().getId())) {
                                    controlAnswers.add(ca);
                                }
                            }
                        }
                        passed = evaluateThreshold(t, controlAnswers);
                    }
                    thresholdDetails.put(t.getRuleDescription() + " [" + t.getType() + " " + t.getValue() + "%]", passed);
                    if (!passed) compliant = false;
                }
                if (covered == 0) compliant = false;
            } else if (covered == 0) {
                compliant = false;
            }

            ComplianceResult r = new ComplianceResult(compliant, thresholdDetails, checked);
            r.setControlsAnswered(covered);
            r.setControlsTotal(totalControls);
            r.setCoveragePercent(round(coveragePercent,2));
            r.setAveragePercent(round(averagePercent,2));

            Map<String,Object> calcDetails = new HashMap<>();
            
            // Build maturity distribution from latest assessment
            Map<String, Integer> maturityDist = new HashMap<>();
            if (details != null && details.getControlAnswers() != null) {
                for (AssessmentControlAnswer ans : details.getControlAnswers()) {
                    if (ans.getMaturityAnswer() != null) {
                        String answerText = ans.getMaturityAnswer().getAnswer();
                        maturityDist.put(answerText, maturityDist.getOrDefault(answerText, 0) + 1);
                    }
                }
            }
            if (!maturityDist.isEmpty()) {
                calcDetails.put("maturityDistribution", maturityDist);
            }
            
            // Build list of latest assessment control answers
            List<Map<String,Object>> assessmentAnswers = new ArrayList<>();
            if (details != null && details.getControlAnswers() != null) {
                for (AssessmentControlAnswer ans : details.getControlAnswers()) {
                    if (ans.getSecurityControl() != null && controlIds.contains(ans.getSecurityControl().getId())) {
                        Map<String,Object> answerMap = new HashMap<>();
                        answerMap.put("controlName", ans.getSecurityControl().getName());
                        answerMap.put("answerText", ans.getMaturityAnswer() != null ? ans.getMaturityAnswer().getAnswer() : "Not answered");
                        answerMap.put("score", ans.getScore());
                        assessmentAnswers.add(answerMap);
                    }
                }
            }
            if (!assessmentAnswers.isEmpty()) {
                calcDetails.put("latestAssessmentAnswers", assessmentAnswers);
            }
            
            r.setCalculationDetails(calcDetails);
            r.setCalculationSummary(String.format("Latest assessment=%s; answered=%d/%d; coverage=%.2f%%; avg=%.2f",
                    a != null ? a.getId() : null, covered, totalControls, r.getCoveragePercent(), r.getAveragePercent()));

            System.out.println(String.format("Unit %s: covered=%d, totalControls=%d, coveragePercent=%.2f, avg=%.2f, checkedAssessments=%d",
                    unit.getId(), covered, totalControls, r.getCoveragePercent(), r.getAveragePercent(), checked));

            resultMap.put(unit, r);

            // totals accumulation
            totalControlsAnswered += covered;
            totalControlsPossible += totalControls;
            totalAvgSum += r.getAveragePercent();
            totalAvgCount++;
            totalAssessmentsCount += checked;
        }

        double totalCoveragePercent = (totalControlsPossible == 0) ? 0.0 : ((double) totalControlsAnswered * 100.0) / (double) totalControlsPossible;
        double totalAveragePercent = (totalAvgCount == 0) ? 0.0 : totalAvgSum / totalAvgCount;
        totalCoveragePercent = round(totalCoveragePercent,2);
        totalAveragePercent = round(totalAveragePercent,2);

        this.latestTotalCoveragePercent = totalCoveragePercent;
        this.latestTotalAveragePercent = totalAveragePercent;
        this.latestTotalAssessmentsCount = totalAssessmentsCount;

        System.out.println(String.format("Totals: totalControlsAnswered=%d, totalControlsPossible=%d, totalCoveragePercent=%.2f, totalAveragePercent=%.2f, totalAssessments=%d",
                totalControlsAnswered, totalControlsPossible, totalCoveragePercent, totalAveragePercent, totalAssessmentsCount));

        return resultMap;
    }

    // Calculate compliance for orgUnit (and its children recursively) for a given ComplianceCheck
    public ComplianceResult calculateCompliance(ComplianceCheck check, OrgUnit orgUnit) {
        Map<OrgUnit, ComplianceResult> complianceResults = evaluateComplianceForOrgAndChildren(orgUnit, check, check.getSecurityCatalog());
        boolean aggregateCompliant = true;
        for (ComplianceResult cr : complianceResults.values()) {
            if (!cr.isCompliant()) { aggregateCompliant = false; break; }
        }
        ComplianceResult orgResult = complianceResults.get(orgUnit);
        if (orgResult == null) return new ComplianceResult(aggregateCompliant, new HashMap<>(), 0);
        ComplianceResult agg = new ComplianceResult(aggregateCompliant, orgResult.getThresholdsDetails(), orgResult.getCheckedAssessments());
        agg.setControlsAnswered(orgResult.getControlsAnswered());
        agg.setControlsTotal(orgResult.getControlsTotal());
        agg.setCoveragePercent(orgResult.getCoveragePercent());
        agg.setAveragePercent(orgResult.getAveragePercent());
        agg.setCalculationDetails(orgResult.getCalculationDetails());
        agg.setCalculationSummary(orgResult.getCalculationSummary());
        return agg;
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
            for (OrgUnit child : parent.getChildren()) collectWithChildrenRecursive(child, list);
        }
    }

    // Utility (round double to x decimals)
    private double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }

}
