package com.govinc.util;

import com.govinc.entity.OpenAIConfiguration;
import com.govinc.entity.OpenAIConfigurationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.json.JSONObject;
import java.util.*;

@Component
public class OpenAIUtil {
    private final OpenAIConfigurationRepository configRepository;

    @Autowired
    public OpenAIUtil(OpenAIConfigurationRepository configRepository) {
        this.configRepository = configRepository;
    }

    /**
     * Sends a prompt to OpenAI API using saved configuration.
     * @param prompt The user's prompt.
     * @return The response from OpenAI API, or error.
     */
    public String askAI(String prompt) {
        Optional<OpenAIConfiguration> configOpt = configRepository.findAll().stream().findFirst();
        if (!configOpt.isPresent()) {
            return "No OpenAI configuration found.";
        }
        OpenAIConfiguration config = configOpt.get();
        String apiKey = config.getApiKey();
        String model = config.getDefaultModel() != null ? config.getDefaultModel() : "gpt-3.5-turbo";
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return "No OpenAI API key is configured. Please set the API key and save.";
        } else if (!apiKey.trim().startsWith("sk-")) {
            return "The OpenAI API key must start with 'sk-'. Please check your key.";
        }
        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = "https://api.openai.com/v1/chat/completions";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey.trim());
            JSONObject requestObj = new JSONObject();
            requestObj.put("model", model);
            requestObj.put("messages", List.of(Map.of("role", "user", "content", prompt)));
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
        } catch(Exception e) {
            return "Error calling OpenAI: " + e.getMessage();
        }
    }
}
