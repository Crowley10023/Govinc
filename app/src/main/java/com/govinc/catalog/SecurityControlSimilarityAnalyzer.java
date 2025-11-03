package com.govinc.catalog;

import com.govinc.util.OpenAIUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for analyzing similarity between security controls using AI and text-based methods.
 * Provides matching rates and suggestions for control consolidation.
 * 
 * ALWAYS uses AI when available to comprehensively check all existing controls.
 */
@Service
public class SecurityControlSimilarityAnalyzer {
    
    @Autowired
    private SecurityControlService securityControlService;
    
    @Autowired(required = false)
    private OpenAIUtil openAIUtil;
    
    /**
     * Analyze a to-be-imported security control against existing controls
     * ALWAYS uses AI if available to comprehensively check all existing controls
     * @param importControl Control to be imported
     * @return AnalysisResult containing similarity matches and recommendations
     */
    public SimilarityAnalysisResult analyzeControl(SecurityControlImportDTO importControl) {
        System.out.println("\n================================================");
        System.out.println("[ANALYZER] analyzeControl() called for: " + importControl.getName());
        System.out.println("[ANALYZER] Detail: " + (importControl.getDetail() != null ? importControl.getDetail().substring(0, Math.min(50, importControl.getDetail().length())) : "N/A"));
        System.out.println("[ANALYZER] Fetching all existing controls from database...");
        
        List<SecurityControl> existingControls = securityControlService.findAll();
        System.out.println("[ANALYZER] Found " + existingControls.size() + " existing controls in database");
        
        SimilarityAnalysisResult result = new SimilarityAnalysisResult();
        result.setImportedControl(importControl);
        
        if (existingControls.isEmpty()) {
            System.out.println("[ANALYZER] No existing controls, returning 'new' action");
            result.setHasSimilarControls(false);
            result.setRecommendedAction("new");
            result.setMatchingRate("none");
            result.setAiAnalysis("No existing controls to compare against");
            System.out.println("================================================\n");
            return result;
        }
        
        // Calculate text similarity scores for all existing controls
        System.out.println("[ANALYZER] Calculating text similarity for all " + existingControls.size() + " controls...");
        List<SimilarityMatch> allMatches = new ArrayList<>();
        for (SecurityControl existingControl : existingControls) {
            double textSimilarity = calculateTextSimilarity(importControl, existingControl);
            SimilarityMatch match = new SimilarityMatch();
            match.setExistingControlId(existingControl.getId());
            match.setExistingControlName(existingControl.getName());
            match.setExistingControlDetail(existingControl.getDetail());
            match.setTextSimilarityScore(textSimilarity);
            allMatches.add(match);
        }
        
        // Sort by similarity score (highest first)
        allMatches.sort((a, b) -> Double.compare(b.getTextSimilarityScore(), a.getTextSimilarityScore()));
        
        System.out.println("[ANALYZER] Text similarity calculated. Top 3 matches:");
        for (int i = 0; i < Math.min(3, allMatches.size()); i++) {
            System.out.println("[ANALYZER]   " + (i+1) + ". " + allMatches.get(i).getExistingControlName() + " (score: " + String.format("%.2f", allMatches.get(i).getTextSimilarityScore()) + ")");
        }
        
        // Get top match for initial assessment
        SimilarityMatch topMatch = allMatches.get(0);
        String matchingRate = determineMatchingRate(topMatch.getTextSimilarityScore());
        result.setMatchingRate(matchingRate);
        result.setHasSimilarControls(topMatch.getTextSimilarityScore() >= 0.4);
        System.out.println("[ANALYZER] Top match: " + topMatch.getExistingControlName() + " (score: " + String.format("%.2f", topMatch.getTextSimilarityScore()) + ", rate: " + matchingRate + ")");
        
        // ALWAYS use AI if available for comprehensive analysis of all controls
        System.out.println("[ANALYZER] Checking if AI provider is available...");
        if (openAIUtil != null) {
            System.out.println("[ANALYZER] AI provider IS AVAILABLE - performing AI analysis");
            performAIAnalysis(importControl, allMatches, result);
        } else {
            System.out.println("[ANALYZER] NO AI provider configured - using text-based analysis only");
            // Fallback to text-based analysis only if no AI provider
            List<SimilarityMatch> textMatches = allMatches.stream()
                .filter(m -> m.getTextSimilarityScore() >= 0.4)
                .limit(3)
                .collect(Collectors.toList());
            result.setMatches(textMatches);
            result.setAiAnalysis("No AI provider configured - using text-based similarity only");
            System.out.println("[ANALYZER] Text-based matches found: " + textMatches.size());
        }
        
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
     * Calculate text similarity using various string comparison methods
     */
    private double calculateTextSimilarity(SecurityControlImportDTO importControl, SecurityControl existingControl) {
        // Combine multiple similarity metrics
        double nameSimilarity = stringSimilarity(
            importControl.getName().toLowerCase(),
            existingControl.getName().toLowerCase()
        );
        
        double detailSimilarity = 0;
        if (importControl.getDetail() != null && existingControl.getDetail() != null) {
            detailSimilarity = stringSimilarity(
                importControl.getDetail().toLowerCase(),
                existingControl.getDetail().toLowerCase()
            );
        }
        
        double referenceSimilarity = 0;
        if (importControl.getReference() != null && existingControl.getReference() != null && 
            !importControl.getReference().isEmpty() && !existingControl.getReference().isEmpty()) {
            referenceSimilarity = importControl.getReference().equalsIgnoreCase(existingControl.getReference()) ? 1.0 : 0;
        }
        
        // Weighted average: name is most important (50%), detail (35%), reference (15%)
        return (nameSimilarity * 0.5) + (detailSimilarity * 0.35) + (referenceSimilarity * 0.15);
    }
    
    /**
     * Calculate string similarity using Levenshtein distance
     */
    private double stringSimilarity(String a, String b) {
        if (a.equals(b)) return 1.0;
        if (a.length() < 2 || b.length() < 2) return 0.0;
        
        // Use Levenshtein distance for similarity calculation
        int maxLength = Math.max(a.length(), b.length());
        int distance = levenshteinDistance(a, b);
        return 1.0 - ((double) distance / maxLength);
    }
    
    /**
     * Calculate Levenshtein distance between two strings
     */
    private int levenshteinDistance(String a, String b) {
        if (a.isEmpty()) return b.length();
        if (b.isEmpty()) return a.length();
        
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1),     // insertion
                    dp[i - 1][j - 1] + cost // substitution
                );
            }
        }
        
        return dp[a.length()][b.length()];
    }
    
    /**
     * Determine matching rate category based on similarity score
     */
    private String determineMatchingRate(double score) {
        if (score >= 0.8) return "high";
        if (score >= 0.6) return "medium";
        if (score >= 0.4) return "low";
        return "none";
    }
    
    /**
     * Perform AI analysis for comprehensive control matching
     * Checks the import control against ALL existing controls using AI
     */
    private void performAIAnalysis(SecurityControlImportDTO importControl, 
                                    List<SimilarityMatch> allMatches, 
                                    SimilarityAnalysisResult result) {
        try {
            System.out.println("\n[AI ANALYSIS] Starting comprehensive AI analysis for control: " + importControl.getName());
            System.out.println("[AI ANALYSIS] Checking against " + allMatches.size() + " existing controls");
            
            // Get top candidates by text similarity for AI to review
            List<SimilarityMatch> topCandidates = allMatches.stream()
                .limit(5)  // Check top 5 candidates
                .collect(Collectors.toList());
            
            System.out.println("[AI ANALYSIS] Using top " + topCandidates.size() + " candidates for AI analysis");
            
            // Build comprehensive prompt for AI
            StringBuilder prompt = new StringBuilder();
            prompt.append("You are a security control expert. Determine if the following control to be imported ");
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
            
            prompt.append("=== TOP CANDIDATE EXISTING CONTROLS ===\n");
            for (int i = 0; i < topCandidates.size(); i++) {
                SimilarityMatch match = topCandidates.get(i);
                prompt.append("\nCandidate ").append((i + 1)).append(":\n");
                prompt.append("  Name: ").append(match.getExistingControlName()).append("\n");
                if (match.getExistingControlDetail() != null && !match.getExistingControlDetail().isEmpty()) {
                    prompt.append("  Detail: ").append(match.getExistingControlDetail()).append("\n");
                }
                prompt.append("  Text Similarity Score: ").append(String.format("%.2f", match.getTextSimilarityScore())).append("\n");
            }
            
            prompt.append("\n=== ANALYSIS REQUEST ===\n");
            prompt.append("Based on semantic similarity and control objectives, analyze if the imported control ");
            prompt.append("is similar to any existing controls.\n\n");
            prompt.append("IMPORTANT: Consider the control names, descriptions, and objectives.\n");
            prompt.append("Do NOT just look at text similarity scores - use domain expertise.\n\n");
            prompt.append("Respond in this exact format:\n");
            prompt.append("MATCH: [YES/NO/PARTIAL]\n");
            prompt.append("CANDIDATE: [NUMBER of best match, or NONE]\n");
            prompt.append("CONFIDENCE: [HIGH/MEDIUM/LOW]\n");
            prompt.append("REASONING: [Brief 1-2 sentence explanation]\n");
            
            System.out.println("[AI ANALYSIS] Prompt built, sending to AI provider...");
            System.out.println("[AI ANALYSIS] Prompt preview (first 200 chars): " + prompt.toString().substring(0, Math.min(200, prompt.length())));
            
            String aiAnalysis = openAIUtil.askAI(prompt.toString());
            System.out.println("[AI ANALYSIS] AI Response received:");
            System.out.println("[AI ANALYSIS] " + aiAnalysis);
            
            result.setAiAnalysis(aiAnalysis);
            
            // Parse AI response to extract match and confidence
            String responseUpper = aiAnalysis.toUpperCase();
            List<SimilarityMatch> recommendedMatches = new ArrayList<>();
            
            if (responseUpper.contains("MATCH: YES") || responseUpper.contains("MATCH:YES")) {
                System.out.println("[AI ANALYSIS] AI confirmed MATCH: YES");
                // AI confirmed a match - extract candidate number
                int candidateNum = extractCandidateNumber(aiAnalysis);
                if (candidateNum > 0 && candidateNum <= topCandidates.size()) {
                    recommendedMatches.add(topCandidates.get(candidateNum - 1));
                    System.out.println("[AI ANALYSIS] AI confirmed match with candidate " + candidateNum + ": " + topCandidates.get(candidateNum - 1).getExistingControlName());
                } else {
                    // If no valid candidate specified, use top match
                    recommendedMatches.add(topCandidates.get(0));
                    System.out.println("[AI ANALYSIS] AI confirmed match, using top candidate: " + topCandidates.get(0).getExistingControlName());
                }
            } else if (responseUpper.contains("MATCH: PARTIAL") || responseUpper.contains("MATCH:PARTIAL")) {
                System.out.println("[AI ANALYSIS] AI found MATCH: PARTIAL");
                // Partial match - include top 2 candidates
                recommendedMatches.addAll(topCandidates.stream().limit(2).collect(Collectors.toList()));
                System.out.println("[AI ANALYSIS] Including top 2 candidates for partial match");
            } else {
                System.out.println("[AI ANALYSIS] AI found MATCH: NO");
                System.out.println("[AI ANALYSIS] No match found");
            }
            
            result.setMatches(recommendedMatches);
            System.out.println("[AI ANALYSIS] AI analysis complete. Recommended matches: " + recommendedMatches.size());
            
            // Update matching rate if AI found something different than text analysis
            if (!recommendedMatches.isEmpty()) {
                double topScore = recommendedMatches.get(0).getTextSimilarityScore();
                String aiDeterminedRate = determineMatchingRate(topScore);
                if (!aiDeterminedRate.equals("none")) {
                    result.setMatchingRate(aiDeterminedRate);
                }
                result.setHasSimilarControls(true);
            }
            
        } catch (Exception e) {
            System.err.println("\n[AI ANALYSIS ERROR] Error during AI analysis: " + e.getMessage());
            e.printStackTrace();
            
            // Fallback to text-based analysis if AI fails
            List<SimilarityMatch> textMatches = allMatches.stream()
                .filter(m -> m.getTextSimilarityScore() >= 0.4)
                .limit(3)
                .collect(Collectors.toList());
            result.setMatches(textMatches);
            result.setAiAnalysis("AI analysis failed - using text-based similarity only. Error: " + e.getMessage());
            System.err.println("[AI ANALYSIS ERROR] Falling back to text-based analysis with " + textMatches.size() + " matches");
        }
    }
    
    /**
     * Extract candidate number from AI response
     */
    private int extractCandidateNumber(String aiResponse) {
        // Look for "CANDIDATE: [NUMBER]" pattern
        String[] lines = aiResponse.split("\n");
        for (String line : lines) {
            if (line.toUpperCase().contains("CANDIDATE:")) {
                // Try to extract number
                String[] parts = line.split(":");
                if (parts.length > 1) {
                    String numStr = parts[1].trim();
                    // Remove any non-digit characters
                    numStr = numStr.replaceAll("[^0-9]", "");
                    try {
                        int num = Integer.parseInt(numStr);
                        System.out.println("[AI ANALYSIS] Extracted candidate number: " + num);
                        return num;
                    } catch (NumberFormatException e) {
                        System.out.println("[AI ANALYSIS] Could not parse candidate number from: " + parts[1]);
                    }
                }
            }
        }
        System.out.println("[AI ANALYSIS] No valid candidate number found in AI response");
        return -1;
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
        private double textSimilarityScore;
        
        // Getters and Setters
        public Long getExistingControlId() { return existingControlId; }
        public void setExistingControlId(Long existingControlId) { this.existingControlId = existingControlId; }
        
        public String getExistingControlName() { return existingControlName; }
        public void setExistingControlName(String existingControlName) { this.existingControlName = existingControlName; }
        
        public String getExistingControlDetail() { return existingControlDetail; }
        public void setExistingControlDetail(String existingControlDetail) { 
            this.existingControlDetail = existingControlDetail; 
        }
        
        public double getTextSimilarityScore() { return textSimilarityScore; }
        public void setTextSimilarityScore(double textSimilarityScore) { this.textSimilarityScore = textSimilarityScore; }
    }
}
