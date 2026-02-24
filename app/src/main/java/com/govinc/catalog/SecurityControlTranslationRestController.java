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

    @Autowired
    private com.govinc.authorization.AuthorizationService authorizationService;

    private boolean isAuthorized() {
        // Only ADMIN and INFORMATION_SECURITY_MANAGER should be able to call translation endpoints
        return authorizationService != null && authorizationService.canAccessSecurityFramework();
    }
    
    /**
     * Cache for translation requests to avoid redundant API calls during import
     * Static cache persists across multiple requests during import sessions
     * Key format: "language:name:detail:domain" (hashed)
     * Cache entries expire after 1 hour
     */
    private static final Map<String, CacheEntry> translationCache = new java.util.concurrent.ConcurrentHashMap<>();
    
    /**
     * Maximum cache entries to prevent unbounded growth
     */
    private static final int MAX_CACHE_SIZE = 10000;
    
    /**
     * Cache entry expiration time in milliseconds (1 hour)
     */
    private static final long CACHE_TTL_MS = 3600000;
    
    /**
     * Inner class to hold cached translation data with timestamp
     */
    private static class CacheEntry {
        Map<String, String> data;
        long timestamp;
        
        CacheEntry(Map<String, String> data) {
            this.data = new HashMap<>(data);
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > CACHE_TTL_MS;
        }
    }
    
    /**
     * Detect the language of security control content
     */
    @PostMapping("/detect-language")
    public ResponseEntity<Map<String, Object>> detectLanguage(
            @RequestBody LanguageDetectionRequest request) {
        if (!isAuthorized()) {
            return ResponseEntity.status(403).body(Map.of("success", false, "error", "forbidden"));
        }
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            System.out.println("\n========================================");
            System.out.println("[DETECT-LANGUAGE] Detection request received");
            System.out.println("[DETECT-LANGUAGE] Content length: " + (request.getContent() != null ? request.getContent().length() : 0));
            System.out.println("========================================");
            
            if (request.getContent() == null || request.getContent().isEmpty()) {
                response.put("success", false);
                response.put("error", "Content is required for language detection");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Build language detection prompt
            String prompt = buildLanguageDetectionPrompt(request.getContent());
            
            System.out.println("[DETECT-LANGUAGE] Calling AI for language detection...");
            String aiResponse = openAIUtil.askAI(prompt);
            System.out.println("[DETECT-LANGUAGE] AI Response received, length: " + aiResponse.length());
            
            // Parse AI response
            Map<String, Object> detectionResult = parseLanguageDetectionResponse(aiResponse);
            
            response.put("success", true);
            response.putAll(detectionResult);
            
            System.out.println("[DETECT-LANGUAGE] Detected language: " + detectionResult.get("detectedLanguage"));
            System.out.println("[DETECT-LANGUAGE] Confidence: " + detectionResult.get("confidence"));
            System.out.println("========================================\n");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.err.println("\n========================================");
            System.err.println("[DETECT-LANGUAGE ERROR] Detection failed: " + e.getMessage());
            System.err.println("========================================");
            e.printStackTrace();
            
            response.put("success", false);
            response.put("error", "Language detection failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Translate security control content to specified language using AI
     * Uses caching to avoid redundant translations for identical input during import
     */
    @PostMapping("/translate")
    public ResponseEntity<Map<String, Object>> translateControl(
            @RequestBody TranslationRequest request) {
        if (!isAuthorized()) {
            return ResponseEntity.status(403).body(Map.of("success", false, "error", "forbidden"));
        }
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            System.out.println("\n========================================");
            System.out.println("[TRANSLATE] Translation request received");
            System.out.println("[TRANSLATE] Language: " + request.getLanguage());
            System.out.println("[TRANSLATE] Name: " + request.getName());
            System.out.println("[TRANSLATE] Detail: " + (request.getDetail() != null ? request.getDetail().substring(0, Math.min(50, request.getDetail().length())) : "N/A"));
            System.out.println("[TRANSLATE] Domain: " + request.getDomain());
            System.out.println("[TRANSLATE] Cache size: " + translationCache.size());
            System.out.println("========================================");
            
            if (request.getLanguage() == null || request.getLanguage().isEmpty()) {
                response.put("success", false);
                response.put("error", "Language is required");
                return ResponseEntity.badRequest().body(response);
            }
            
            if ((request.getName() == null || request.getName().isEmpty()) &&
                (request.getDetail() == null || request.getDetail().isEmpty()) &&
                (request.getDomain() == null || request.getDomain().isEmpty())) {
                response.put("success", false);
                response.put("error", "At least name, detail, or domain must be provided");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Generate cache key from request data
            String cacheKey = generateTranslationCacheKey(request);
            System.out.println("[TRANSLATE] Cache key: " + cacheKey);
            
            // Check if translation is already cached and not expired
            CacheEntry cacheEntry = translationCache.get(cacheKey);
            if (cacheEntry != null && !cacheEntry.isExpired()) {
                System.out.println("[TRANSLATE] Cache HIT - returning cached translation (cache size: " + translationCache.size() + ")");
                response.put("success", true);
                response.put("translated", new HashMap<>(cacheEntry.data));
                response.put("language", request.getLanguage());
                response.put("cached", true);
                System.out.println("========================================\n");
                return ResponseEntity.ok(response);
            } else if (cacheEntry != null && cacheEntry.isExpired()) {
                // Remove expired entry
                translationCache.remove(cacheKey);
                System.out.println("[TRANSLATE] Removed expired cache entry");
            }
            
            // Build translation prompt
            String languageLabel = getLanguageLabel(request.getLanguage());
            String prompt = buildTranslationPrompt(request.getName(), request.getDetail(), request.getDomain(), languageLabel);
            
            System.out.println("[TRANSLATE] Cache MISS - calling AI with prompt... (cache size before: " + translationCache.size() + ")");
            String aiResponse = openAIUtil.askAI(prompt);
            System.out.println("[TRANSLATE] AI Response received, length: " + aiResponse.length());
            
            // Parse AI response
            Map<String, String> translated = parseTranslationResponse(aiResponse);
            
            // Store in cache for future identical requests (with size limit)
            if (translationCache.size() < MAX_CACHE_SIZE) {
                translationCache.put(cacheKey, new CacheEntry(translated));
                System.out.println("[TRANSLATE] Translation cached for future use (cache size after: " + translationCache.size() + ")");
            } else {
                System.out.println("[TRANSLATE] Cache is at maximum size (" + MAX_CACHE_SIZE + "), not caching this entry");
            }
            
            response.put("success", true);
            response.put("translated", translated);
            response.put("language", request.getLanguage());
            response.put("cached", false);
            
            System.out.println("[TRANSLATE] Translation successful");
            System.out.println("[TRANSLATE] Translated name: " + translated.get("name"));
            System.out.println("[TRANSLATE] Translated detail: " + (translated.get("detail") != null ? translated.get("detail").substring(0, Math.min(50, translated.get("detail").length())) : "N/A"));
            System.out.println("[TRANSLATE] Translated domain: " + translated.get("domain"));
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
     * Generate a cache key for translation requests
     * Combines language and content for unique identification
     * Normalizes input for lenient matching (case-insensitive, trimmed, normalized whitespace)
     * Static method allows access across instances
     */
    private static String generateTranslationCacheKey(TranslationRequest request) {
        StringBuilder key = new StringBuilder();
        
        // Normalize language (case-insensitive)
        key.append(normalizeString(request.getLanguage())).append(":");
        
        // Normalize content fields (trim, lowercase, normalize whitespace)
        key.append(normalizeString(request.getName())).append(":");
        key.append(normalizeString(request.getDetail())).append(":");
        key.append(normalizeString(request.getDomain()));
        
        // Use hash to keep key length manageable
        return "TRANS_" + Integer.toHexString(key.toString().hashCode());
    }
    
    /**
     * Normalize a string for lenient cache matching
     * Handles null values, trims whitespace, converts to lowercase, and normalizes internal spaces
     */
    private static String normalizeString(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        // Trim, convert to lowercase, and normalize multiple spaces to single space
        return input.trim().toLowerCase().replaceAll("\\s+", " ");
    }
    
    /**
     * Clear translation cache (optional, can be called after import completes)
     * Static method to ensure cache is cleared globally
     */
    public static void clearTranslationCache() {
        System.out.println("[CACHE] Clearing translation cache. Size before: " + translationCache.size());
        translationCache.clear();
        System.out.println("[CACHE] Cache cleared. Size after: " + translationCache.size());
    }
    
    /**
     * Clear expired cache entries to free up memory
     * Should be called periodically to maintain cache health
     */
    public static void clearExpiredCacheEntries() {
        int initialSize = translationCache.size();
        translationCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        int removedCount = initialSize - translationCache.size();
        if (removedCount > 0) {
            System.out.println("[CACHE] Removed " + removedCount + " expired entries. Cache size: " + translationCache.size());
        }
    }
    
    /**
     * Get current cache size for monitoring
     */
    public static int getCacheSize() {
        return translationCache.size();
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
    private String buildTranslationPrompt(String name, String detail, String domain, String targetLanguage) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Translate the following security control information to ").append(targetLanguage).append(".\n");
        prompt.append("Respond ONLY with a valid JSON object containing 'name', 'detail', and 'domain' keys. Do not include markdown or any other text.\n\n");
        
        prompt.append("Original security control:\n");
        if (name != null && !name.isEmpty()) {
            prompt.append("Name: ").append(name).append("\n");
        }
        if (detail != null && !detail.isEmpty()) {
            prompt.append("Detail: ").append(detail).append("\n");
        }
        if (domain != null && !domain.isEmpty()) {
            prompt.append("Domain: ").append(domain).append("\n");
        }
        
        prompt.append("\nReturn format (valid JSON only):\n");
        prompt.append("{\"name\": \"translated name here\", \"detail\": \"translated detail here\", \"domain\": \"translated domain here\"}\n\n");
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
            result.put("domain", json.optString("domain", ""));
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
                result.put("domain", jsonObj.optString("domain", ""));
            } catch (JSONException e2) {
                System.err.println("[TRANSLATE] Failed to parse AI response as JSON: " + response);
                throw new JSONException("Invalid translation response format: " + e2.getMessage());
            }
        }
        
        return result;
    }
    
    /**
     * Build language detection prompt for AI
     */
    private String buildLanguageDetectionPrompt(String content) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Detect the primary language of the following text.\n");
        prompt.append("Respond ONLY with a valid JSON object containing 'detectedLanguage' (language code like 'en', 'de', 'fr'), ");
        prompt.append("'languageName' (full name like 'English'), and 'confidence' (0.0 to 1.0) keys.\n");
        prompt.append("Do not include markdown or any other text.\n\n");
        
        prompt.append("Text to analyze:\n");
        prompt.append(content).append("\n\n");
        
        prompt.append("Return format (valid JSON only):\n");
        prompt.append("{\"detectedLanguage\": \"en\", \"languageName\": \"English\", \"confidence\": 0.95}\n\n");
        prompt.append("Important: Return ONLY the JSON object, no explanations or markdown code blocks.");
        
        return prompt.toString();
    }
    
    /**
     * Parse AI language detection response
     */
    private Map<String, Object> parseLanguageDetectionResponse(String response) throws JSONException {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Try to parse as JSON directly
            JSONObject json = new JSONObject(response);
            result.put("detectedLanguage", json.optString("detectedLanguage", "unknown"));
            result.put("languageName", json.optString("languageName", "Unknown"));
            result.put("confidence", json.optDouble("confidence", 0.0));
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
                result.put("detectedLanguage", jsonObj.optString("detectedLanguage", "unknown"));
                result.put("languageName", jsonObj.optString("languageName", "Unknown"));
                result.put("confidence", jsonObj.optDouble("confidence", 0.0));
            } catch (JSONException e2) {
                System.err.println("[DETECT-LANGUAGE] Failed to parse AI response as JSON: " + response);
                throw new JSONException("Invalid language detection response format: " + e2.getMessage());
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
        private String domain;
        
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
        
        public String getDomain() {
            return domain;
        }
        
        public void setDomain(String domain) {
            this.domain = domain;
        }
    }
    
    /**
     * Language detection request DTO
     */
    public static class LanguageDetectionRequest {
        private String content;
        private String method;
        
        // Getters and Setters
        public String getContent() {
            return content;
        }
        
        public void setContent(String content) {
            this.content = content;
        }
        
        public String getMethod() {
            return method;
        }
        
        public void setMethod(String method) {
            this.method = method;
        }
    }
}
