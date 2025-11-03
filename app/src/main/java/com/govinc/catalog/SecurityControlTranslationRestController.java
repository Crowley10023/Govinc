package com.govinc.catalog;

import com.govinc.util.OpenAIUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.*;

/**
 * REST API endpoints for security control translation operations
 */
@RestController
@RequestMapping("/api/security-control")
public class SecurityControlTranslationRestController {
    
    @Autowired
    private OpenAIUtil openAIUtil;
    
    /**
     * Translate security control content to specified language using AI
     */
    @PostMapping("/translate")
    public ResponseEntity<Map<String, Object>> translateControl(
            @RequestBody TranslationRequest request) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            System.out.println("\n========================================");
            System.out.println("[TRANSLATE] Translation request received");
            System.out.println("[TRANSLATE] Language: " + request.getLanguage());
            System.out.println("[TRANSLATE] Name: " + request.getName());
            System.out.println("[TRANSLATE] Detail: " + (request.getDetail() != null ? request.getDetail().substring(0, Math.min(50, request.getDetail().length())) : "N/A"));
            System.out.println("========================================");
            
            if (request.getLanguage() == null || request.getLanguage().isEmpty()) {
                response.put("success", false);
                response.put("error", "Language is required");
                return ResponseEntity.badRequest().body(response);
            }
            
            if ((request.getName() == null || request.getName().isEmpty()) &&
                (request.getDetail() == null || request.getDetail().isEmpty())) {
                response.put("success", false);
                response.put("error", "At least name or detail must be provided");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Build translation prompt
            String languageLabel = getLanguageLabel(request.getLanguage());
            String prompt = buildTranslationPrompt(request.getName(), request.getDetail(), languageLabel);
            
            System.out.println("[TRANSLATE] Calling AI with prompt...");
            String aiResponse = openAIUtil.askAI(prompt);
            System.out.println("[TRANSLATE] AI Response received, length: " + aiResponse.length());
            
            // Parse AI response
            Map<String, String> translated = parseTranslationResponse(aiResponse);
            
            response.put("success", true);
            response.put("translated", translated);
            response.put("language", request.getLanguage());
            
            System.out.println("[TRANSLATE] Translation successful");
            System.out.println("[TRANSLATE] Translated name: " + translated.get("name"));
            System.out.println("[TRANSLATE] Translated detail: " + (translated.get("detail") != null ? translated.get("detail").substring(0, Math.min(50, translated.get("detail").length())) : "N/A"));
            System.out.println("========================================\n");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.err.println("\n========================================");
            System.err.println("[TRANSLATE ERROR] Translation failed: " + e.getMessage());
            System.err.println("========================================");
            e.printStackTrace();
            
            response.put("success", false);
            response.put("error", "Translation failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Get language label from language code
     */
    private String getLanguageLabel(String languageCode) {
        switch (languageCode.toLowerCase()) {
            case "de":
                return "German";
            case "en":
                return "English";
            case "fr":
                return "French";
            case "es":
                return "Spanish";
            case "it":
                return "Italian";
            case "pt":
                return "Portuguese";
            case "nl":
                return "Dutch";
            case "ja":
                return "Japanese";
            case "zh":
                return "Chinese";
            default:
                return languageCode;
        }
    }
    
    /**
     * Build translation prompt for AI
     */
    private String buildTranslationPrompt(String name, String detail, String targetLanguage) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Translate the following security control information to ").append(targetLanguage).append(".\n");
        prompt.append("Respond ONLY with a valid JSON object containing 'name' and 'detail' keys. Do not include markdown or any other text.\n\n");
        
        prompt.append("Original security control:\n");
        if (name != null && !name.isEmpty()) {
            prompt.append("Name: ").append(name).append("\n");
        }
        if (detail != null && !detail.isEmpty()) {
            prompt.append("Detail: ").append(detail).append("\n");
        }
        
        prompt.append("\nReturn format (valid JSON only):\n");
        prompt.append("{\"name\": \"translated name here\", \"detail\": \"translated detail here\"}\n\n");
        prompt.append("Important: Return ONLY the JSON object, no explanations or markdown code blocks.");
        
        return prompt.toString();
    }
    
    /**
     * Parse AI translation response
     */
    private Map<String, String> parseTranslationResponse(String response) throws JSONException {
        Map<String, String> result = new HashMap<>();
        
        try {
            // Try to parse as JSON directly
            JSONObject json = new JSONObject(response);
            result.put("name", json.optString("name", ""));
            result.put("detail", json.optString("detail", ""));
        } catch (JSONException e1) {
            // Try to extract JSON from response (in case AI wrapped it in markdown)
            String json = response;
            
            // Remove markdown code blocks if present
            if (json.contains("```json")) {
                json = json.substring(json.indexOf("```json") + 7);
                if (json.contains("```")) {
                    json = json.substring(0, json.indexOf("```"));
                }
            } else if (json.contains("```")) {
                json = json.substring(json.indexOf("```") + 3);
                if (json.contains("```")) {
                    json = json.substring(0, json.indexOf("```"));
                }
            }
            
            json = json.trim();
            
            // Try again with cleaned JSON
            try {
                JSONObject jsonObj = new JSONObject(json);
                result.put("name", jsonObj.optString("name", ""));
                result.put("detail", jsonObj.optString("detail", ""));
            } catch (JSONException e2) {
                System.err.println("[TRANSLATE] Failed to parse AI response as JSON: " + response);
                throw new JSONException("Invalid translation response format: " + e2.getMessage());
            }
        }
        
        return result;
    }
    
    /**
     * Translation request DTO
     */
    public static class TranslationRequest {
        private String language;
        private String name;
        private String detail;
        
        // Getters and Setters
        public String getLanguage() {
            return language;
        }
        
        public void setLanguage(String language) {
            this.language = language;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getDetail() {
            return detail;
        }
        
        public void setDetail(String detail) {
            this.detail = detail;
        }
    }
}
