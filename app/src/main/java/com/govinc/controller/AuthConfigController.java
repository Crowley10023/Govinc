package com.govinc.controller;

import com.govinc.service.AuthConfigService;
import com.govinc.service.AuthProviderHealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/admin/auth-config")
public class AuthConfigController {
    private static final Logger logger = LoggerFactory.getLogger(AuthConfigController.class);
    
    @Autowired
    private AuthConfigService authConfigService;
    
    @Autowired
    private AuthProviderHealthService healthService;
    
    @GetMapping
    public String authConfigPage(Model model) {
        var providers = authConfigService.getAvailableProviders();
        model.addAttribute("providers", providers);
        return "auth-config";
    }
    
    @GetMapping("/status")
    @ResponseBody
    public Map<String, Object> getAuthStatus() {
        Map<String, Object> status = new HashMap<>();
        var providers = authConfigService.getAvailableProviders();
        
        Map<String, Map<String, Object>> providerStatus = new HashMap<>();
        
        for (var entry : providers.entrySet()) {
            var provider = entry.getValue();
            var healthResult = healthService.getLastHealthCheckResult(entry.getKey());
            
            Map<String, Object> info = new HashMap<>();
            info.put("displayName", provider.getDisplayName());
            info.put("type", provider.getType().toString());
            info.put("configured", provider.isConfigured());
            info.put("healthy", provider.isHealthy());
            
            // Add masked configuration details
            if (provider.isConfigured()) {
                info.put("clientId", maskString(provider.getClientId()));
                if (provider.getRedirectUri() != null) {
                    info.put("redirectUri", provider.getRedirectUri());
                }
                if (provider.getIssuerUri() != null) {
                    info.put("issuerUri", provider.getIssuerUri());
                }
                if (provider.getTenantId() != null) {
                    info.put("tenantId", provider.getTenantId());
                }
            }
            
            // Add health check details if available
            if (healthResult != null) {
                Map<String, Object> healthCheck = new HashMap<>();
                healthCheck.put("errorCode", healthResult.getErrorCode());
                healthCheck.put("message", healthResult.getMessage());
                healthCheck.put("details", healthResult.getDetails());
                healthCheck.put("timestamp", healthResult.getFormattedTimestamp());
                info.put("healthCheck", healthCheck);
            }
            
            providerStatus.put(entry.getKey(), info);
        }
        
        status.put("providers", providerStatus);
        status.put("hasOAuth2", authConfigService.hasOAuth2Providers());
        
        return status;
    }
    
    private String maskString(String value) {
        if (value == null || value.length() <= 4) {
            return "***";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }
    
    @PostMapping("/keycloak")
    @ResponseBody
    public ResponseEntity<Map<String, String>> configureKeycloak(
            @RequestParam String clientId,
            @RequestParam String clientSecret,
            @RequestParam String issuerUri) {
        
        logger.info("Configuring Keycloak provider via web interface");
        
        try {
            AuthConfigService.AuthProvider keycloak = new AuthConfigService.AuthProvider(
                "keycloak",
                "Keycloak",
                clientId,
                clientSecret,
                issuerUri,
                null,
                AuthConfigService.AuthProviderType.KEYCLOAK
            );
            
            authConfigService.addOrUpdateProvider(keycloak);
            
            // Perform health check
            healthService.checkProviderHealthAsync(keycloak);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Keycloak provider configured successfully. Application restart may be required for full integration.");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to configure Keycloak provider", e);
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to configure Keycloak: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @PostMapping("/azure")
    @ResponseBody
    public ResponseEntity<Map<String, String>> configureAzure(
            @RequestParam String clientId,
            @RequestParam String clientSecret,
            @RequestParam String tenantId) {
        
        logger.info("Configuring Azure provider via web interface");
        
        try {
            AuthConfigService.AuthProvider azure = new AuthConfigService.AuthProvider(
                "azure",
                "Microsoft Azure",
                clientId,
                clientSecret,
                null,
                tenantId,
                AuthConfigService.AuthProviderType.AZURE
            );
            
            authConfigService.addOrUpdateProvider(azure);
            
            // Perform health check
            healthService.checkProviderHealthAsync(azure);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Azure provider configured successfully. Application restart may be required for full integration.");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to configure Azure provider", e);
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to configure Azure: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @DeleteMapping("/provider/{providerId}")
    @ResponseBody
    public ResponseEntity<Map<String, String>> removeProvider(@PathVariable String providerId) {
        logger.info("Removing provider {} via web interface", providerId);
        
        try {
            authConfigService.removeProvider(providerId);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Provider removed successfully. Application restart may be required.");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to remove provider {}", providerId, e);
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to remove provider: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @PostMapping("/health-check")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> performHealthCheck() {
        logger.info("Performing manual health check via web interface");
        
        try {
            healthService.checkAllProvidersHealth();
            
            // Wait a bit for async health checks to complete
            Thread.sleep(2000);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Health check completed");
            
            // Include detailed results
            Map<String, Object> results = new HashMap<>();
            var healthResults = healthService.getAllHealthCheckResults();
            
            for (var entry : healthResults.entrySet()) {
                var result = entry.getValue();
                Map<String, Object> resultInfo = new HashMap<>();
                resultInfo.put("healthy", result.isHealthy());
                resultInfo.put("errorCode", result.getErrorCode());
                resultInfo.put("message", result.getMessage());
                resultInfo.put("details", result.getDetails());
                resultInfo.put("timestamp", result.getFormattedTimestamp());
                results.put(entry.getKey(), resultInfo);
            }
            
            response.put("results", results);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Health check failed", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Health check failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    @GetMapping("/health/{providerId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getProviderHealth(@PathVariable String providerId) {
        var healthResult = healthService.getLastHealthCheckResult(providerId);
        
        if (healthResult == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "No health check data available for provider: " + providerId);
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("providerId", providerId);
        response.put("healthy", healthResult.isHealthy());
        response.put("errorCode", healthResult.getErrorCode());
        response.put("message", healthResult.getMessage());
        response.put("details", healthResult.getDetails());
        response.put("timestamp", healthResult.getFormattedTimestamp());
        
        return ResponseEntity.ok(response);
    }
    
    @PutMapping("/provider/{providerId}/redirect-uri")
    @ResponseBody
    public ResponseEntity<Map<String, String>> updateRedirectUri(
            @PathVariable String providerId,
            @RequestParam String redirectUri) {
        
        logger.info("Updating redirect-uri for provider {} via web interface", providerId);
        
        try {
            var existingProvider = authConfigService.getProvider(providerId);
            if (existingProvider == null) {
                Map<String, String> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Provider not found: " + providerId);
                return ResponseEntity.notFound().build();
            }
            
            // Create updated provider with new redirect URI
            AuthConfigService.AuthProvider updatedProvider = new AuthConfigService.AuthProvider(
                existingProvider.getId(),
                existingProvider.getDisplayName(),
                existingProvider.getClientId(),
                existingProvider.getClientSecret(),
                existingProvider.getIssuerUri(),
                existingProvider.getTenantId(),
                redirectUri,
                existingProvider.getType()
            );
            
            authConfigService.addOrUpdateProvider(updatedProvider);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Redirect URI updated successfully for " + providerId + ". Application restart may be required for full integration.");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to update redirect URI for provider {}", providerId, e);
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to update redirect URI: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}