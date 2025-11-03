package com.govinc.catalog;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API endpoints for security control import operations
 */
@RestController
@RequestMapping("/api/security-control/import")
public class SecurityControlImportRestController {
    
    @Autowired
    private SecurityControlSimilarityAnalyzer similarityAnalyzer;
    
    /**
     * Analyze a batch of security controls for similarity
     * Returns similarity analysis for each control to help with merge decisions
     */
    @PostMapping("/analyze")
    public ResponseEntity<List<SecurityControlSimilarityAnalyzer.SimilarityAnalysisResult>> analyzeControls(
            @RequestBody List<SecurityControlSimilarityAnalyzer.SecurityControlImportDTO> controls) {
        
        try {
            System.out.println("\n========================================");
            System.out.println("[API] POST /api/security-control/import/analyze called");
            System.out.println("[API] Received " + (controls != null ? controls.size() : 0) + " controls to analyze");
            System.out.println("========================================");
            
            if (controls == null || controls.isEmpty()) {
                System.out.println("[API] Controls list is empty, returning bad request");
                return ResponseEntity.badRequest().build();
            }
            
            System.out.println("[API] Starting analysis for " + controls.size() + " controls");
            List<SecurityControlSimilarityAnalyzer.SimilarityAnalysisResult> results = new ArrayList<>();
            
            for (int i = 0; i < controls.size(); i++) {
                SecurityControlSimilarityAnalyzer.SecurityControlImportDTO control = controls.get(i);
                System.out.println("\n[API] Analyzing control " + (i+1) + "/" + controls.size() + ": " + control.getName());
                System.out.println("[API]   Detail: " + (control.getDetail() != null ? control.getDetail().substring(0, Math.min(50, control.getDetail().length())) : "N/A"));
                
                SecurityControlSimilarityAnalyzer.SimilarityAnalysisResult result = similarityAnalyzer.analyzeControl(control);
                
                System.out.println("[API]   Result - Match Rate: " + result.getMatchingRate());
                System.out.println("[API]   Result - Recommended Action: " + result.getRecommendedAction());
                System.out.println("[API]   Result - Matches Found: " + result.getMatches().size());
                System.out.println("[API]   Result - AI Analysis: " + (result.getAiAnalysis() != null ? result.getAiAnalysis().substring(0, Math.min(100, result.getAiAnalysis().length())) : "N/A"));
                
                results.add(result);
            }
            
            System.out.println("\n[API] All analyses complete, returning " + results.size() + " results");
            System.out.println("========================================\n");
            return ResponseEntity.ok(results);
            
        } catch (Exception e) {
            System.err.println("\n========================================");
            System.err.println("[API ERROR] Error analyzing controls: " + e.getMessage());
            System.err.println("========================================");
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Analyze a single security control for similarity
     */
    @PostMapping("/analyze-single")
    public ResponseEntity<SecurityControlSimilarityAnalyzer.SimilarityAnalysisResult> analyzeSingleControl(
            @RequestBody SecurityControlSimilarityAnalyzer.SecurityControlImportDTO control) {
        
        try {
            System.out.println("\n========================================");
            System.out.println("[API] POST /api/security-control/import/analyze-single called");
            System.out.println("[API] Analyzing single control: " + (control != null ? control.getName() : "null"));
            System.out.println("========================================");
            
            if (control == null) {
                System.out.println("[API] Control is null, returning bad request");
                return ResponseEntity.badRequest().build();
            }
            
            SecurityControlSimilarityAnalyzer.SimilarityAnalysisResult result = similarityAnalyzer.analyzeControl(control);
            
            System.out.println("[API] Analysis complete - Match Rate: " + result.getMatchingRate());
            System.out.println("[API] Recommended Action: " + result.getRecommendedAction());
            System.out.println("[API] Matches Found: " + result.getMatches().size());
            System.out.println("========================================\n");
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            System.err.println("\n========================================");
            System.err.println("[API ERROR] Error analyzing single control: " + e.getMessage());
            System.err.println("========================================");
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}
