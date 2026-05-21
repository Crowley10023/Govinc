package com.govinc.governance;

import com.govinc.assessment.Assessment;
import com.govinc.assessment.AssessmentControlAnswer;
import com.govinc.assessment.AssessmentDetails;
import com.govinc.assessment.AssessmentDetailsService;
import com.govinc.assessment.AssessmentRepository;
import com.govinc.catalog.SecurityCatalog;
import com.govinc.catalog.SecurityControl;
import com.govinc.compliance.ComplianceCheck;
import com.govinc.compliance.ComplianceService;
import com.govinc.compliance.ComplianceThreshold;
import com.govinc.organization.OrgUnit;
import com.govinc.util.OpenAIUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DeviationAnalysisService {

    @Autowired
    private ComplianceService complianceService;

    @Autowired
    private AssessmentRepository assessmentRepository;

    @Autowired
    private AssessmentDetailsService assessmentDetailsService;

    @Autowired
    private OpenAIUtil openAIUtil;

    /**
     * A single gap item in the deviation analysis result.
     */
    public static class GapItem {
        private List<SecurityControl> relatedControls;
        private String gapIndication;
        private String recommendation;
        private String taskProposal;
        private String domainName;
        private int currentScore;
        private int requiredScore;

        public List<SecurityControl> getRelatedControls() { return relatedControls; }
        public void setRelatedControls(List<SecurityControl> relatedControls) { this.relatedControls = relatedControls; }
        public String getGapIndication() { return gapIndication; }
        public void setGapIndication(String gapIndication) { this.gapIndication = gapIndication; }
        public String getRecommendation() { return recommendation; }
        public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
        public String getTaskProposal() { return taskProposal; }
        public void setTaskProposal(String taskProposal) { this.taskProposal = taskProposal; }
        public String getDomainName() { return domainName; }
        public void setDomainName(String domainName) { this.domainName = domainName; }
        public int getCurrentScore() { return currentScore; }
        public void setCurrentScore(int currentScore) { this.currentScore = currentScore; }
        public int getRequiredScore() { return requiredScore; }
        public void setRequiredScore(int requiredScore) { this.requiredScore = requiredScore; }
    }

    /**
     * Run deviation analysis for a given org unit, catalog and compliance check.
     * Finds gaps by comparing latest assessment answers against compliance thresholds,
     * then uses AI to group intersecting controls and produce intelligent recommendations.
     */
    public List<GapItem> analyzeDeviations(OrgUnit orgUnit, SecurityCatalog catalog, ComplianceCheck complianceCheck) {
        // 1. Get all org units (include children) and their latest assessments
        List<OrgUnit> units = complianceService.collectWithChildren(orgUnit);
        Map<Long, Assessment> latestAssessments = complianceService.getLatestAssessments(units, catalog);

        // 2. Determine required threshold
        int requiredThreshold = 0;
        if (complianceCheck.getThresholds() != null && !complianceCheck.getThresholds().isEmpty()) {
            for (ComplianceThreshold t : complianceCheck.getThresholds()) {
                if (t.getValue() > requiredThreshold) {
                    requiredThreshold = t.getValue();
                }
            }
        }

        // 3. Collect all controls in the catalog
        List<SecurityControl> catalogControls = catalog.getSecurityControls();
        if (catalogControls == null || catalogControls.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, SecurityControl> controlMap = new HashMap<>();
        for (SecurityControl sc : catalogControls) {
            controlMap.put(sc.getId(), sc);
        }

        // 4. Gather answers from all latest assessments
        // Map: controlId -> best score across all assessments
        Map<Long, Integer> bestScores = new HashMap<>();
        Map<Long, String> controlComments = new HashMap<>();

        for (Assessment assessment : latestAssessments.values()) {
            if (assessment == null) continue;
            Assessment freshAssessment = assessmentRepository.findById(assessment.getId()).orElse(assessment);
            AssessmentDetails details = assessmentDetailsService.findByAssessmentId(freshAssessment.getId()).orElse(null);
            if (details == null || details.getControlAnswers() == null) continue;

            for (AssessmentControlAnswer answer : details.getControlAnswers()) {
                if (answer.getSecurityControl() == null) continue;
                Long ctrlId = answer.getSecurityControl().getId();
                if (!controlMap.containsKey(ctrlId)) continue;

                int score = answer.getScore();
                bestScores.merge(ctrlId, score, Math::max);
                if (answer.getComment() != null && !answer.getComment().isBlank()) {
                    controlComments.put(ctrlId, answer.getComment());
                }
            }
        }

        // 5. Identify gaps: controls below threshold or not answered at all
        List<SecurityControl> gapControls = new ArrayList<>();
        Map<Long, Integer> gapScores = new HashMap<>();

        for (SecurityControl control : catalogControls) {
            int score = bestScores.getOrDefault(control.getId(), 0);
            if (score < requiredThreshold) {
                gapControls.add(control);
                gapScores.put(control.getId(), score);
            }
        }

        if (gapControls.isEmpty()) {
            return Collections.emptyList();
        }

        // 6. Group gaps by domain for intelligent analysis
        Map<String, List<SecurityControl>> byDomain = new LinkedHashMap<>();
        for (SecurityControl ctrl : gapControls) {
            String domain = ctrl.getSecurityControlDomain() != null
                    ? ctrl.getSecurityControlDomain().getName() : "Uncategorized";
            byDomain.computeIfAbsent(domain, k -> new ArrayList<>()).add(ctrl);
        }

        // 7. Use AI to produce intelligent, grouped gap analysis
        List<GapItem> results = new ArrayList<>();

        // Build a prompt describing all gaps for AI analysis
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a security governance expert. Analyze the following security control gaps and produce a consolidated deviation analysis. ");
        prompt.append("Security controls that intersect or focus on the same topic should be grouped together into a single finding. ");
        prompt.append("Required compliance threshold: ").append(requiredThreshold).append("%. ");
        prompt.append("\n\nGaps found:\n");

        for (Map.Entry<String, List<SecurityControl>> entry : byDomain.entrySet()) {
            prompt.append("\nDomain: ").append(entry.getKey()).append("\n");
            for (SecurityControl ctrl : entry.getValue()) {
                int score = gapScores.getOrDefault(ctrl.getId(), 0);
                prompt.append("- Control: ").append(ctrl.getName());
                if (ctrl.getDetail() != null) {
                    String detail = ctrl.getDetail().length() > 200 ? ctrl.getDetail().substring(0, 200) + "..." : ctrl.getDetail();
                    prompt.append(" (").append(detail).append(")");
                }
                prompt.append(" | Current score: ").append(score).append("%");
                prompt.append(" | Required: ").append(requiredThreshold).append("%");
                String comment = controlComments.get(ctrl.getId());
                if (comment != null) {
                    String shortComment = comment.length() > 100 ? comment.substring(0, 100) + "..." : comment;
                    prompt.append(" | Comment: ").append(shortComment);
                }
                prompt.append("\n");
            }
        }

        prompt.append("\nFor each consolidated finding, output EXACTLY in this format (one finding per block, separate blocks with ---):\n");
        prompt.append("CONTROLS: <comma-separated control names that belong to this finding>\n");
        prompt.append("GAP: <one-sentence gap indication>\n");
        prompt.append("RECOMMENDATION: <one short actionable recommendation>\n");
        prompt.append("TASK: <concise task title for creating a remediation task>\n");

        String aiResponse = null;
        try {
            aiResponse = openAIUtil.askAI(prompt.toString());
        } catch (Exception e) {
            // AI unavailable - fall back to simple per-control gaps
        }

        if (aiResponse != null && !aiResponse.isBlank()) {
            results = parseAIResponse(aiResponse, controlMap, gapScores, byDomain, requiredThreshold);
        }

        // Fallback or supplement: if AI returned nothing, produce simple per-domain gaps
        if (results.isEmpty()) {
            for (Map.Entry<String, List<SecurityControl>> entry : byDomain.entrySet()) {
                GapItem item = new GapItem();
                item.setRelatedControls(entry.getValue());
                item.setDomainName(entry.getKey());

                int worstScore = entry.getValue().stream()
                        .mapToInt(c -> gapScores.getOrDefault(c.getId(), 0))
                        .min().orElse(0);
                item.setCurrentScore(worstScore);
                item.setRequiredScore(requiredThreshold);

                String controlNames = entry.getValue().stream()
                        .map(SecurityControl::getName)
                        .collect(Collectors.joining(", "));
                item.setGapIndication("Controls below threshold in domain '" + entry.getKey() + "': " + controlNames);
                item.setRecommendation("Review and improve controls in " + entry.getKey() + " domain to meet compliance threshold.");
                item.setTaskProposal("Remediate " + entry.getKey() + " gaps (" + entry.getValue().size() + " controls)");
                results.add(item);
            }
        }

        return results;
    }

    /**
     * Parse the AI response into structured GapItems.
     */
    private List<GapItem> parseAIResponse(String response, Map<Long, SecurityControl> controlMap,
                                           Map<Long, Integer> gapScores,
                                           Map<String, List<SecurityControl>> byDomain,
                                           int requiredThreshold) {
        List<GapItem> items = new ArrayList<>();
        // Build name->control lookup
        Map<String, SecurityControl> nameToControl = new HashMap<>();
        for (SecurityControl ctrl : controlMap.values()) {
            nameToControl.put(ctrl.getName().toLowerCase().trim(), ctrl);
        }

        String[] blocks = response.split("---");
        for (String block : blocks) {
            block = block.trim();
            if (block.isEmpty()) continue;

            GapItem item = new GapItem();
            String controls = "";
            String gap = "";
            String rec = "";
            String task = "";

            for (String line : block.split("\n")) {
                line = line.trim();
                if (line.toUpperCase().startsWith("CONTROLS:")) {
                    controls = line.substring("CONTROLS:".length()).trim();
                } else if (line.toUpperCase().startsWith("GAP:")) {
                    gap = line.substring("GAP:".length()).trim();
                } else if (line.toUpperCase().startsWith("RECOMMENDATION:")) {
                    rec = line.substring("RECOMMENDATION:".length()).trim();
                } else if (line.toUpperCase().startsWith("TASK:")) {
                    task = line.substring("TASK:".length()).trim();
                }
            }

            if (gap.isEmpty() && rec.isEmpty()) continue;

            // Resolve control names to actual SecurityControl objects
            List<SecurityControl> related = new ArrayList<>();
            if (!controls.isEmpty()) {
                for (String name : controls.split(",")) {
                    String key = name.trim().toLowerCase();
                    SecurityControl match = nameToControl.get(key);
                    if (match == null) {
                        // Try partial match
                        for (Map.Entry<String, SecurityControl> e : nameToControl.entrySet()) {
                            if (e.getKey().contains(key) || key.contains(e.getKey())) {
                                match = e.getValue();
                                break;
                            }
                        }
                    }
                    if (match != null) related.add(match);
                }
            }

            item.setRelatedControls(related);
            item.setGapIndication(gap);
            item.setRecommendation(rec);
            item.setTaskProposal(task);

            // Calculate scores from related controls
            if (!related.isEmpty()) {
                int worstScore = related.stream()
                        .mapToInt(c -> gapScores.getOrDefault(c.getId(), 0))
                        .min().orElse(0);
                item.setCurrentScore(worstScore);
                item.setRequiredScore(requiredThreshold);

                // Domain from first control
                SecurityControl first = related.get(0);
                item.setDomainName(first.getSecurityControlDomain() != null
                        ? first.getSecurityControlDomain().getName() : "");
            } else {
                item.setCurrentScore(0);
                item.setRequiredScore(requiredThreshold);
            }

            items.add(item);
        }
        return items;
    }
}
