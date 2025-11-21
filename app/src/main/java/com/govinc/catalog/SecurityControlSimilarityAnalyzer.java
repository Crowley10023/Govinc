package com.govinc.catalog;

import com.govinc.util.OpenAIUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for analyzing similarity between security controls using AI.
 * Provides matching rates and suggestions for control consolidation.
 * 
 * ALWAYS uses AI to comprehensively check all existing controls against a new control.
 */
@Service
public class SecurityControlSimilarityAnalyzer {
    
    @Autowired
    private SecurityControlService securityControlService;
    
    @Autowired(required = false)
    private OpenAIUtil openAIUtil;
    
    /**
     * Analyze a to-be-imported security control against existing controls
     * ALWAYS uses AI to comprehensively check all existing controls
     * @param importControl Control to be imported
     * @return AnalysisResult containing similarity matches and recommendations
     */
    public SimilarityAnalysisResult analyzeControl(SecurityControlImportDTO importControl) {
        List<SecurityControl> existingControls = securityControlService.findAll();
        
        SimilarityAnalysisResult result = new SimilarityAnalysisResult();
        result.setImportedControl(importControl);
        
        if (existingControls.isEmpty()) {
            result.setHasSimilarControls(false);
            result.setRecommendedAction("new");
            result.setMatchingRate("none");
            result.setAiAnalysis("No existing controls to compare against");
            return result;
        }
        
        // Check AI provider availability
        System.out.println("[ANALYZER] Checking if AI provider is available...");
        if (openAIUtil == null) {
            System.out.println("[ANALYZER] ERROR: No AI provider configured - cannot proceed");
            throw new IllegalStateException("AI provider (OpenAIUtil) is required for security control analysis but is not configured");
        }
        
        System.out.println("[ANALYZER] AI provider IS AVAILABLE - performing AI analysis");
        System.out.println("[ANALYZER] Analyzing against " + existingControls.size() + " existing controls");
        
        // Perform comprehensive AI analysis against ALL controls
        performAIAnalysisOnAllControls(importControl, existingControls, result);
        
        // Recommend action based on results
        if (!result.getMatches().isEmpty()) {
            result.setRecommendedAction("merge");
            result.setSuggestedMergeControl(result.getMatches().get(0));
            System.out.println("[ANALYZER] Recommending MERGE with: " + result.getMatches().get(0).getExistingControlName());
        } else {
            result.setRecommendedAction("new");
            System.out.println("[ANALYZER] Recommending CREATE NEW (no matches)");
        }
        
        System.out.println("================================================\n");
        return result;
    }
    
    /**
     * Perform AI analysis for comprehensive control matching
     * Checks the import control against ALL existing controls using AI
     */
    private void performAIAnalysisOnAllControls(SecurityControlImportDTO importControl, 
                                                 List<SecurityControl> allControls, 
                                                 SimilarityAnalysisResult result) {
        try {
            System.out.println("\n[AI ANALYSIS] Starting comprehensive AI analysis for control: " + importControl.getName());
            System.out.println("[AI ANALYSIS] Checking against " + allControls.size() + " existing controls");
            
            // Build comprehensive prompt for AI to analyze against ALL controls
            StringBuilder prompt = new StringBuilder();
            prompt.append("You are a security control expert. Determine if the following control to be imported \n");
            prompt.append("is similar to or should be merged with any existing security controls.\n\n");
            
            prompt.append("=== CONTROL TO BE IMPORTED ===\n");
            prompt.append("Name: ").append(importControl.getName()).append("\n");
            if (importControl.getDetail() != null && !importControl.getDetail().isEmpty()) {
                prompt.append("Detail: ").append(importControl.getDetail()).append("\n");
            }
            if (importControl.getReference() != null && !importControl.getReference().isEmpty()) {
                prompt.append("Reference: ").append(importControl.getReference()).append("\n");
            }
            if (importControl.getDomain() != null && !importControl.getDomain().isEmpty()) {
                prompt.append("Domain: ").append(importControl.getDomain()).append("\n");
            }
            prompt.append("\n");
            
            prompt.append("=== ALL EXISTING SECURITY CONTROLS ===\n");
            for (int i = 0; i < allControls.size(); i++) {
                SecurityControl control = allControls.get(i);
                prompt.append("\n").append((i + 1)).append(".");
                prompt.append(" ID: ").append(control.getId());
                prompt.append(" | Name: ").append(control.getName());
                if (control.getDetail() != null && !control.getDetail().isEmpty()) {
                    prompt.append(" | Detail: ").append(control.getDetail());
                }
                if (control.getReference() != null && !control.getReference().isEmpty()) {
                    prompt.append(" | Reference: ").append(control.getReference());
                }
                prompt.append("\n");
            }
            
            prompt.append("\n=== ANALYSIS REQUEST ===\n");
            prompt.append("Analyze the imported control against ALL listed existing controls.\n");
            prompt.append("Use semantic similarity and control objectives to identify matches.\n");
            prompt.append("Consider control names, descriptions, objectives, and domains.\n\n");
            prompt.append("Respond in this exact format:\n");
            prompt.append("MATCHES: [Comma-separated list of control IDs that match, or NONE]\n");
            prompt.append("PRIMARY_MATCH: [ID of the best match, or NONE]\n");
            prompt.append("CONFIDENCE: [HIGH/MEDIUM/LOW]\n");
            prompt.append("REASONING: [Brief explanation of which controls match and why]\n");
            
            System.out.println("[AI ANALYSIS] Analyzing against ALL " + allControls.size() + " controls...");
            System.out.println("[AI ANALYSIS] Sending comprehensive prompt to AI provider...");
            
            String aiAnalysis = openAIUtil.askAI(prompt.toString());
            System.out.println("[AI ANALYSIS] AI Response received:");
            System.out.println("[AI ANALYSIS] " + aiAnalysis);
            
            result.setAiAnalysis(aiAnalysis);
            
            // Parse AI response to extract matches
            List<SimilarityMatch> recommendedMatches = parseAIAnalysisResponse(aiAnalysis, allControls);
            result.setMatches(recommendedMatches);
            
            // Determine matching rate and control flags
            if (!recommendedMatches.isEmpty()) {
                String confidence = extractConfidenceLevel(aiAnalysis);
                String matchingRate = mapConfidenceToRate(confidence);
                result.setMatchingRate(matchingRate);
                result.setHasSimilarControls(true);
                System.out.println("[AI ANALYSIS] AI identified " + recommendedMatches.size() + " matching controls with confidence: " + confidence);
            } else {
                result.setMatchingRate("none");
                result.setHasSimilarControls(false);
                System.out.println("[AI ANALYSIS] No matching controls identified");
            }
            
            System.out.println("[AI ANALYSIS] AI analysis complete. Recommended matches: " + recommendedMatches.size());
            
        } catch (Exception e) {
            System.err.println("\n[AI ANALYSIS ERROR] Error during AI analysis: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("AI analysis failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Parse AI response to extract matching control IDs
     */
    private List<SimilarityMatch> parseAIAnalysisResponse(String aiResponse, List<SecurityControl> allControls) {
        List<SimilarityMatch> matches = new ArrayList<>();
        
        try {
            // Extract PRIMARY_MATCH ID
            String primaryMatchId = extractPrimaryMatchId(aiResponse);
            
            if (primaryMatchId != null && !primaryMatchId.equalsIgnoreCase("NONE")) {
                try {
                    long matchId = Long.parseLong(primaryMatchId);
                    SecurityControl matchedControl = allControls.stream()
                        .filter(c -> c.getId() == matchId)
                        .findFirst()
                        .orElse(null);
                    
                    if (matchedControl != null) {
                        SimilarityMatch match = new SimilarityMatch();
                        match.setExistingControlId(matchedControl.getId());
                        match.setExistingControlName(matchedControl.getName());
                        match.setExistingControlDetail(matchedControl.getDetail());
                        match.setAiSimilarityScore(1.0); // AI confirmed match
                        matches.add(match);
                        System.out.println("[AI ANALYSIS] Extracted primary match ID: " + matchId);
                    }
                } catch (NumberFormatException e) {
                    System.out.println("[AI ANALYSIS] Could not parse primary match ID: " + primaryMatchId);
                }
            }
            
            // Extract additional MATCHES
            List<String> additionalMatchIds = extractAdditionalMatchIds(aiResponse);
            for (String matchIdStr : additionalMatchIds) {
                try {
                    long matchId = Long.parseLong(matchIdStr);
                    // Skip if already in primary match
                    if (matches.stream().anyMatch(m -> m.getExistingControlId() == matchId)) {
                        continue;
                    }
                    
                    SecurityControl matchedControl = allControls.stream()
                        .filter(c -> c.getId() == matchId)
                        .findFirst()
                        .orElse(null);
                    
                    if (matchedControl != null) {
                        SimilarityMatch match = new SimilarityMatch();
                        match.setExistingControlId(matchedControl.getId());
                        match.setExistingControlName(matchedControl.getName());
                        match.setExistingControlDetail(matchedControl.getDetail());
                        match.setAiSimilarityScore(0.8); // Secondary match
                        matches.add(match);
                        System.out.println("[AI ANALYSIS] Extracted additional match ID: " + matchId);
                    }
                } catch (NumberFormatException e) {
                    System.out.println("[AI ANALYSIS] Could not parse match ID: " + matchIdStr);
                }
            }
            
        } catch (Exception e) {
            System.err.println("[AI ANALYSIS] Error parsing AI response: " + e.getMessage());
        }
        
        return matches;
    }
    
    /**
     * Extract primary match ID from AI response
     */
    private String extractPrimaryMatchId(String aiResponse) {
        String[] lines = aiResponse.split("\n");
        for (String line : lines) {
            if (line.toUpperCase().contains("PRIMARY_MATCH:")) {
                String[] parts = line.split(":");
                if (parts.length > 1) {
                    String id = parts[1].trim();
                    // Remove any non-digit characters except comma
                    id = id.replaceAll("[^0-9]", "");
                    if (!id.isEmpty()) {
                        return id;
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Extract additional match IDs from MATCHES line
     */
    private List<String> extractAdditionalMatchIds(String aiResponse) {
        List<String> ids = new ArrayList<>();
        String[] lines = aiResponse.split("\n");
        for (String line : lines) {
            if (line.toUpperCase().contains("MATCHES:")) {
                String[] parts = line.split(":");
                if (parts.length > 1) {
                    String idString = parts[1].trim();
                    if (!idString.equalsIgnoreCase("NONE")) {
                        // Split by comma and parse each ID
                        String[] idArray = idString.split(",");
                        for (String id : idArray) {
                            id = id.trim().replaceAll("[^0-9]", "");
                            if (!id.isEmpty()) {
                                ids.add(id);
                            }
                        }
                    }
                }
            }
        }
        return ids;
    }
    
    /**
     * Extract confidence level from AI response
     */
    private String extractConfidenceLevel(String aiResponse) {
        String[] lines = aiResponse.split("\n");
        for (String line : lines) {
            if (line.toUpperCase().contains("CONFIDENCE:")) {
                String[] parts = line.split(":");
                if (parts.length > 1) {
                    String confidence = parts[1].trim().toUpperCase();
                    if (confidence.contains("HIGH")) return "HIGH";
                    if (confidence.contains("MEDIUM")) return "MEDIUM";
                    if (confidence.contains("LOW")) return "LOW";
                }
            }
        }
        return "MEDIUM";
    }
    
    /**
     * Map confidence level to matching rate
     */
    private String mapConfidenceToRate(String confidence) {
        switch (confidence.toUpperCase()) {
            case "HIGH":
                return "high";
            case "MEDIUM":
                return "medium";
            case "LOW":
                return "low";
            default:
                return "medium";
        }
    }
    
    /**
     * DTO for import control data
     */
    public static class SecurityControlImportDTO {
        private String name;
        private String detail;
        private String reference;
        private String domain;
        
        public SecurityControlImportDTO() {}
        
        public SecurityControlImportDTO(String name, String detail, String reference, String domain) {
            this.name = name;
            this.detail = detail;
            this.reference = reference;
            this.domain = domain;
        }
        
        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getDetail() { return detail; }
        public void setDetail(String detail) { this.detail = detail; }
        
        public String getReference() { return reference; }
        public void setReference(String reference) { this.reference = reference; }
        
        public String getDomain() { return domain; }
        public void setDomain(String domain) { this.domain = domain; }
    }
    
    /**
     * Result of similarity analysis
     */
    public static class SimilarityAnalysisResult {
        private SecurityControlImportDTO importedControl;
        private String matchingRate; // "high", "medium", "low", "none"
        private String recommendedAction; // "merge", "new"
        private boolean hasSimilarControls;
        private List<SimilarityMatch> matches = new ArrayList<>();
        private SimilarityMatch suggestedMergeControl;
        private String aiAnalysis;
        
        // Getters and Setters
        public SecurityControlImportDTO getImportedControl() { return importedControl; }
        public void setImportedControl(SecurityControlImportDTO importedControl) { this.importedControl = importedControl; }
        
        public String getMatchingRate() { return matchingRate; }
        public void setMatchingRate(String matchingRate) { this.matchingRate = matchingRate; }
        
        public String getRecommendedAction() { return recommendedAction; }
        public void setRecommendedAction(String recommendedAction) { this.recommendedAction = recommendedAction; }
        
        public boolean isHasSimilarControls() { return hasSimilarControls; }
        public void setHasSimilarControls(boolean hasSimilarControls) { this.hasSimilarControls = hasSimilarControls; }
        
        public List<SimilarityMatch> getMatches() { return matches; }
        public void setMatches(List<SimilarityMatch> matches) { this.matches = matches; }
        
        public SimilarityMatch getSuggestedMergeControl() { return suggestedMergeControl; }
        public void setSuggestedMergeControl(SimilarityMatch suggestedMergeControl) { 
            this.suggestedMergeControl = suggestedMergeControl; 
        }
        
        public String getAiAnalysis() { return aiAnalysis; }
        public void setAiAnalysis(String aiAnalysis) { this.aiAnalysis = aiAnalysis; }
    }
    
    /**
     * Individual similarity match
     */
    public static class SimilarityMatch {
        private Long existingControlId;
        private String existingControlName;
        private String existingControlDetail;
        private double aiSimilarityScore;
        
        // Getters and Setters
        public Long getExistingControlId() { return existingControlId; }
        public void setExistingControlId(Long existingControlId) { this.existingControlId = existingControlId; }
        
        public String getExistingControlName() { return existingControlName; }
        public void setExistingControlName(String existingControlName) { this.existingControlName = existingControlName; }
        
        public String getExistingControlDetail() { return existingControlDetail; }
        public void setExistingControlDetail(String existingControlDetail) { 
            this.existingControlDetail = existingControlDetail; 
        }
        
        public double getAiSimilarityScore() { return aiSimilarityScore; }
        public void setAiSimilarityScore(double aiSimilarityScore) { this.aiSimilarityScore = aiSimilarityScore; }
    }
}
