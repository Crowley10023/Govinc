package com.govinc.util;

import com.govinc.entity.AIProvider;
import com.govinc.entity.OpenAIConfiguration;
import com.govinc.entity.AIPromptCache;
import com.govinc.repository.AIProviderRepository;
import com.govinc.repository.OpenAIConfigurationRepository;
import com.govinc.repository.AIPromptCacheRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.*;
import org.json.JSONObject;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * OpenAIUtil handles AI requests by routing them to the configured active
 * provider.
 * Supports multiple providers: OpenAI, Ollama, etc.
 */
@Component
public class OpenAIUtil {
    private final OpenAIConfigurationRepository configRepository;
    private final AIProviderRepository providerRepository;
    private final AIPromptCacheRepository cacheRepository;

    @Autowired
    public OpenAIUtil(OpenAIConfigurationRepository configRepository, AIProviderRepository providerRepository,
            AIPromptCacheRepository cacheRepository) {
        this.configRepository = configRepository;
        this.providerRepository = providerRepository;
        this.cacheRepository = cacheRepository;
    }

    public String askAI(String prompt) {
        return askAI(prompt, true);
    }

    /**
     * Main method to ask AI using the active provider
     * Checks cache first for exact prompt matches before making API calls
     */
    public String askAI(String prompt, boolean cache) {
        System.out.println("prompt: " + prompt);
        OpenAIConfiguration config = configRepository.findAll().stream().findFirst().orElse(null);

        if (config == null || config.getActiveProvider() == null) {
            return "No AI provider configured. Please configure a provider in AI settings.";
        }

        AIProvider provider = config.getActiveProvider();

        if (!provider.isActive()) {
            return "The configured AI provider (" + provider.getDisplayName() + ") is not active.";
        }

        // Check cache for exact prompt match
        if (cache) {
            String cachedResult = getCachedResponse(prompt, provider.getName());
            if (cachedResult != null) {
                System.out.println("Cache hit for prompt: " + prompt);
                return cachedResult;
            }
        }

        String result = routeToProvider(prompt, provider);
        System.out.println("... --> " + result);

        // Cache the result if it's not an error
        if (!result.startsWith("Error") && !result.startsWith("No AI provider")
                && !result.startsWith("The configured")) {
            cacheResponse(prompt, result, provider.getName());
        }

        return result;
    }

    /**
     * Check cache for an exact prompt match
     * Uses SHA-256 hash for exact matching
     */
    private String getCachedResponse(String prompt, String providerName) {
        try {
            String hash = hashPrompt(prompt);
            Optional<AIPromptCache> cached = cacheRepository.findByPromptHashAndProviderName(hash, providerName);
            if (cached.isPresent()) {
                AIPromptCache cache = cached.get();
                cache.recordHit();
                cacheRepository.save(cache);
                return cache.getResponse();
            }
        } catch (Exception e) {
            System.err.println("Error checking cache: " + e.getMessage());
        }
        return null;
    }

    /**
     * Cache a prompt and its response
     */
    private void cacheResponse(String prompt, String response, String providerName) {
        try {
            String hash = hashPrompt(prompt);

            // Check if already exists
            Optional<AIPromptCache> existing = cacheRepository.findByPromptHashAndProviderName(hash, providerName);
            if (existing.isPresent()) {
                AIPromptCache cache = existing.get();
                cache.recordHit();
                cacheRepository.save(cache);
            } else {
                AIPromptCache cache = new AIPromptCache(prompt, hash, response, providerName);
                cacheRepository.save(cache);
                System.out.println("Cached response for prompt: " + prompt);
            }
        } catch (Exception e) {
            System.err.println("Error caching response: " + e.getMessage());
        }
    }

    /**
     * Generate SHA-256 hash of prompt for exact matching
     */
    private String hashPrompt(String prompt) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(prompt.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1)
                hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Routes the AI request to the appropriate provider based on the provider type
     */
    private String routeToProvider(String prompt, AIProvider provider) {
        String providerName = provider.getName();

        if ("openai".equalsIgnoreCase(providerName)) {
            return askOpenAI(prompt, provider);
        } else if ("openai-custom".equalsIgnoreCase(providerName)) {
            return askOpenAICustom(prompt, provider);
        } else if ("ollama".equalsIgnoreCase(providerName)) {
            return askOllama(prompt, provider);
        } else if ("anthropic".equalsIgnoreCase(providerName)) {
            return askAnthropic(prompt, provider);
        } else {
            return "Unsupported AI provider: " + providerName;
        }
    }

    /**
     * Call OpenAI API
     */
    private String askOpenAI(String prompt, AIProvider provider) {
        try {
            String apiKey = provider.getSetting("apiKey");
            String model = provider.getSetting("model");
            String baseUrl = provider.getSetting("baseUrl");

            if (apiKey == null || apiKey.trim().isEmpty()) {
                return "No OpenAI API key configured. Please set the API key in provider settings.";
            }

            if (model == null || model.trim().isEmpty()) {
                model = "gpt-3.5-turbo";
            }

            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                baseUrl = "https://api.openai.com/v1";
            }

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey.trim());

            JSONObject requestObj = new JSONObject();
            requestObj.put("model", model);
            requestObj.put("messages", List.of(Map.of("role", "user", "content", prompt)));

            String url = baseUrl + "/chat/completions";
            HttpEntity<String> entity = new HttpEntity<>(requestObj.toString(), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JSONObject body = new JSONObject(response.getBody());
                return body.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
            } else if (response.getStatusCode().value() == 401) {
                String body = response.getBody();
                String errorMsg = "OpenAI API response: 401 Unauthorized. Your API key is likely missing, invalid, or not active.";
                if (body != null && !body.isEmpty()) {
                    try {
                        JSONObject errorObj = new JSONObject(body);
                        if (errorObj.has("error")) {
                            errorMsg += " Details: " + errorObj.getJSONObject("error").optString("message", body);
                        } else {
                            errorMsg += " Response: " + body;
                        }
                    } catch (Exception e) {
                        errorMsg += " Response: " + body;
                    }
                }
                return errorMsg;
            } else {
                String body = response.getBody();
                String errorMsg = "OpenAI returned code: " + response.getStatusCode();
                if (body != null && !body.isEmpty()) {
                    try {
                        JSONObject errorObj = new JSONObject(body);
                        if (errorObj.has("error")) {
                            errorMsg += ". Error: " + errorObj.getJSONObject("error").optString("message", body);
                        } else {
                            errorMsg += ". Response: " + body;
                        }
                    } catch (Exception e) {
                        errorMsg += ". Response: " + body;
                    }
                }
                return errorMsg;
            }
        } catch (HttpClientErrorException e) {
            String errorMsg = "Error calling OpenAI: " + e.getStatusCode();
            String body = e.getResponseBodyAsString();
            if (body != null && !body.isEmpty()) {
                try {
                    JSONObject errorObj = new JSONObject(body);
                    if (errorObj.has("error")) {
                        errorMsg += ". " + errorObj.getJSONObject("error").optString("message", body);
                    } else {
                        errorMsg += ". Response: " + body;
                    }
                } catch (Exception ex) {
                    errorMsg += ". Response: " + body;
                }
            }
            return errorMsg;
        } catch (Exception e) {
            return "Error calling OpenAI: " + e.getMessage();
        }
    }

    /**
     * Call Ollama API
     */
    private String askOllama(String prompt, AIProvider provider) {
        try {
            String model = provider.getSetting("model");
            String baseUrl = provider.getSetting("baseUrl");

            if (model == null || model.trim().isEmpty()) {
                return "No Ollama model configured. Please set the model in provider settings.";
            }

            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                baseUrl = "http://localhost:11434";
            }

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            JSONObject requestObj = new JSONObject();
            requestObj.put("model", model);
            requestObj.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            // Set stream to false to get the complete response in one go, not streaming
            // tokens
            requestObj.put("stream", false);

            String url = baseUrl + "/api/chat";
            HttpEntity<String> entity = new HttpEntity<>(requestObj.toString(), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JSONObject body = new JSONObject(response.getBody());
                if (body.has("message") && body.getJSONObject("message").has("content")) {
                    return body.getJSONObject("message").getString("content");
                } else if (body.has("message") && body.getJSONObject("message").has("text")) {
                    return body.getJSONObject("message").getString("text");
                }
            } else {
                return "Ollama returned code: " + response.getStatusCode();
            }
        } catch (Exception e) {
            return "Error calling Ollama: " + e.getMessage();
        }
        return "";
    }

    /**
     * Call OpenAI-compatible API with custom headers and configuration
     * Supports proprietary OpenAI-compatible APIs that use custom authentication
     * headers
     */
    private String askOpenAICustom(String prompt, AIProvider provider) {
        String errorMsg = "OpenAI Custom API error";
        try {
            String apiKey = provider.getSetting("apiKey");
            String model = provider.getSetting("model");
            String baseUrl = provider.getSetting("baseUrl");
            String headerName = provider.getSetting("headerName");
            String maxTokens = provider.getSetting("maxTokens");

            if (apiKey == null || apiKey.trim().isEmpty()) {
                return "No API key configured for OpenAI Custom provider.";
            }

            if (model == null || model.trim().isEmpty()) {
                model = "gpt-3.5-turbo";
            }

            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                baseUrl = "https://api.openai.com/v1";
            }

            // Default to "api-key" header if not specified, but allow customization
            if (headerName == null || headerName.trim().isEmpty()) {
                headerName = "api-key";
            }

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(headerName, apiKey.trim());

            JSONObject requestObj = new JSONObject();
            requestObj.put("model", model);
            requestObj.put("messages", List.of(Map.of("role", "user", "content", prompt)));

            // Add max_completion_tokens if configured
            if (maxTokens != null && !maxTokens.trim().isEmpty()) {
                try {
                    requestObj.put("max_completion_tokens", Integer.parseInt(maxTokens));
                } catch (NumberFormatException e) {
                    // Ignore if not a valid number
                }
            }

            String url = baseUrl + "/chat/completions";
            HttpEntity<String> entity = new HttpEntity<>(requestObj.toString(), headers);

            errorMsg += "\n\n" + entity + headers + requestObj.toString() + "\n\n";

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            errorMsg += response;

            if (response.getStatusCode().is2xxSuccessful()) {
                JSONObject body = new JSONObject(response.getBody());
                return body.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
            } else if (response.getStatusCode().value() == 401) {
                String body = response.getBody();
                if (body != null && !body.isEmpty()) {
                    try {
                        JSONObject errorObj = new JSONObject(body);
                        if (errorObj.has("error")) {
                            errorMsg += " Details: " + errorObj.getJSONObject("error").optString("message", body);
                        } else {
                            errorMsg += " Response: " + body;
                        }
                    } catch (Exception e) {
                        errorMsg += " Response: " + body;
                    }
                }
                return errorMsg;
            } else {
                String body = response.getBody();
                errorMsg += response.getStatusCode();
                if (body != null && !body.isEmpty()) {
                    errorMsg += "body \n\n" + body;
                    try {
                        JSONObject errorObj = new JSONObject(body);
                        if (errorObj.has("error")) {
                            errorMsg += ". Error: " + errorObj.getJSONObject("error").optString("message", body);
                        } else {
                            errorMsg += ". Response: " + body;
                        }
                    } catch (Exception e) {
                        errorMsg += ". Response: " + body;
                    }
                }
                return errorMsg;
            }
        } catch (HttpClientErrorException e) {
            errorMsg += e.getStatusCode();
            String body = e.getResponseBodyAsString();
            if (body != null && !body.isEmpty()) {
                try {
                    JSONObject errorObj = new JSONObject(body);
                    if (errorObj.has("error")) {
                        errorMsg += ". " + errorObj.getJSONObject("error").optString("message", body);
                    } else {
                        errorMsg += ". Response: " + body;
                    }
                } catch (Exception ex) {
                    errorMsg += ". Response: " + body;
                }
            }
            return errorMsg;
        } catch (Exception e) {
            return "Error calling OpenAI Custom API: " + e.getMessage() + errorMsg;
        }
    }

    /**
     * Call Anthropic API (placeholder for future implementation)
     */
    private String askAnthropic(String prompt, AIProvider provider) {
        try {
            String apiKey = provider.getSetting("apiKey");
            String model = provider.getSetting("model");

            if (apiKey == null || apiKey.trim().isEmpty()) {
                return "No Anthropic API key configured.";
            }

            if (model == null || model.trim().isEmpty()) {
                model = "claude-3-sonnet-20240229";
            }

            // TODO: Implement Anthropic API call similar to OpenAI
            return "Anthropic provider not yet fully implemented.";
        } catch (Exception e) {
            return "Error calling Anthropic: " + e.getMessage();
        }
    }

    /**
     * Get the currently active provider
     */
    public AIProvider getActiveProvider() {
        return providerRepository.findFirstByActiveTrue().orElse(null);
    }

    /**
     * Get all configured providers
     */
    public List<AIProvider> getAllProviders() {
        return providerRepository.findAll();
    }
}
