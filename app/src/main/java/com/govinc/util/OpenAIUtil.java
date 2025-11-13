package com.govinc.util;

import com.govinc.entity.AIProvider;
import com.govinc.entity.OpenAIConfiguration;
import com.govinc.repository.AIProviderRepository;
import com.govinc.repository.OpenAIConfigurationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.json.JSONObject;
import java.util.*;

/**
 * OpenAIUtil handles AI requests by routing them to the configured active provider.
 * Supports multiple providers: OpenAI, Ollama, etc.
 */
@Component
public class OpenAIUtil {
    private final OpenAIConfigurationRepository configRepository;
    private final AIProviderRepository providerRepository;

    @Autowired
    public OpenAIUtil(OpenAIConfigurationRepository configRepository, AIProviderRepository providerRepository) {
        this.configRepository = configRepository;
        this.providerRepository = providerRepository;
    }

    /**
     * Main method to ask AI using the active provider
     */
    public String askAI(String prompt) {
        System.out.println("prompt: " + prompt);
        OpenAIConfiguration config = configRepository.findAll().stream().findFirst().orElse(null);
        
        if (config == null || config.getActiveProvider() == null) {
            return "No AI provider configured. Please configure a provider in AI settings.";
        }

        AIProvider provider = config.getActiveProvider();
        
        if (!provider.isActive()) {
            return "The configured AI provider (" + provider.getDisplayName() + ") is not active.";
        }

        String result = routeToProvider(prompt, provider);
        System.out.println("... --> " + result);
        return result;
    }

    /**
     * Routes the AI request to the appropriate provider based on the provider type
     */
    private String routeToProvider(String prompt, AIProvider provider) {
        String providerName = provider.getName();

        if ("openai".equalsIgnoreCase(providerName)) {
            return askOpenAI(prompt, provider);
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
                return "OpenAI API response: 401 Unauthorized. Your API key is likely missing, invalid, or not active.";
            } else {
                return "OpenAI returned code: " + response.getStatusCode();
            }
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
            // Set stream to false to get the complete response in one go, not streaming tokens
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
