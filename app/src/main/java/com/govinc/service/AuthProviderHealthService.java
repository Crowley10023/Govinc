package com.govinc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.boot.web.client.RestTemplateBuilder;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

@Service
@EnableAsync
public class AuthProviderHealthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthProviderHealthService.class);
    
    @Autowired
    private AuthConfigService authConfigService;
    
    private final RestTemplate restTemplate;
    private final Map<String, HealthCheckResult> lastHealthCheckResults = new ConcurrentHashMap<>();
    
    public AuthProviderHealthService() {
        this.restTemplate = new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(10))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
    
    @PostConstruct
    public void initialize() {
        // Initial health check for all providers
        checkAllProvidersHealth();
    }
    
    public void checkAllProvidersHealth() {
        var providers = authConfigService.getAvailableProviders();
        
        for (var provider : providers.values()) {
            if (provider.getType() == AuthConfigService.AuthProviderType.FORM) {
                continue; // Form auth is always healthy
            }
            
            checkProviderHealthAsync(provider);
        }
    }
    
    @Async
    public CompletableFuture<Boolean> checkProviderHealthAsync(AuthConfigService.AuthProvider provider) {
        HealthCheckResult result = checkProviderHealthWithDetails(provider);
        provider.setHealthy(result.isHealthy());
        
        // Store the detailed result for later retrieval
        lastHealthCheckResults.put(provider.getId(), result);
        
        if (!result.isHealthy()) {
            logger.warn("Authentication provider {} is not healthy: {} (Code: {}, Details: {})", 
                       provider.getId(), result.getMessage(), result.getErrorCode(), result.getDetails());
        } else {
            logger.debug("Authentication provider {} is healthy: {}", provider.getId(), result.getMessage());
        }
        
        return CompletableFuture.completedFuture(result.isHealthy());
    }
    
    private HealthCheckResult checkProviderHealthWithDetails(AuthConfigService.AuthProvider provider) {
        if (!provider.isConfigured()) {
            return new HealthCheckResult(
                false, 
                "CONFIGURATION_MISSING", 
                "Provider is not properly configured", 
                "Missing required configuration parameters (client-id, client-secret, etc.)"
            );
        }
        
        try {
            return switch (provider.getType()) {
                case KEYCLOAK -> checkKeycloakHealthWithDetails(provider);
                case AZURE -> checkAzureHealthWithDetails(provider);
                case FORM -> new HealthCheckResult(true, "SUCCESS", "Form authentication is always available", null);
            };
        } catch (Exception e) {
            logger.debug("Unexpected error during health check for provider {}: {}", provider.getId(), e.getMessage());
            return new HealthCheckResult(
                false, 
                "UNEXPECTED_ERROR", 
                "Unexpected error during health check", 
                e.getMessage()
            );
        }
    }
    
    @Deprecated
    private boolean checkProviderHealth(AuthConfigService.AuthProvider provider) {
        return checkProviderHealthWithDetails(provider).isHealthy();
    }
    
    private HealthCheckResult checkKeycloakHealthWithDetails(AuthConfigService.AuthProvider provider) {
        try {
            String healthUrl = provider.getIssuerUri() + "/.well-known/openid-configuration";
            String response = restTemplate.getForObject(healthUrl, String.class);
            
            if (response != null && response.contains("issuer")) {
                return new HealthCheckResult(
                    true, 
                    "SUCCESS", 
                    "Keycloak is accessible and responding correctly", 
                    "OpenID configuration endpoint returned valid response"
                );
            } else {
                return new HealthCheckResult(
                    false, 
                    "INVALID_RESPONSE", 
                    "Keycloak returned invalid OpenID configuration", 
                    "Response does not contain expected 'issuer' field"
                );
            }
            
        } catch (HttpClientErrorException e) {
            return new HealthCheckResult(
                false, 
                "HTTP_CLIENT_ERROR_" + e.getStatusCode().value(), 
                "HTTP client error: " + e.getStatusCode() + " " + e.getStatusText(), 
                "URL: " + provider.getIssuerUri() + "/.well-known/openid-configuration, Response: " + e.getResponseBodyAsString()
            );
        } catch (HttpServerErrorException e) {
            return new HealthCheckResult(
                false, 
                "HTTP_SERVER_ERROR_" + e.getStatusCode().value(), 
                "HTTP server error: " + e.getStatusCode() + " " + e.getStatusText(), 
                "Keycloak server is experiencing issues. Response: " + e.getResponseBodyAsString()
            );
        } catch (ResourceAccessException e) {
            String errorCode = "CONNECTION_ERROR";
            String message = "Connection error";
            String details = e.getMessage();
            
            if (e.getCause() instanceof ConnectException) {
                errorCode = "CONNECTION_REFUSED";
                message = "Connection refused - Keycloak server is not reachable";
                details = "Cannot connect to " + provider.getIssuerUri() + ". Server may be down or network issues exist.";
            } else if (e.getCause() instanceof SocketTimeoutException) {
                errorCode = "CONNECTION_TIMEOUT";
                message = "Connection timeout - Keycloak server is not responding";
                details = "Timeout after 10 seconds trying to connect to " + provider.getIssuerUri();
            } else if (e.getCause() instanceof UnknownHostException) {
                errorCode = "UNKNOWN_HOST";
                message = "Unknown host - Cannot resolve Keycloak server address";
                details = "DNS resolution failed for " + provider.getIssuerUri();
            }
            
            return new HealthCheckResult(false, errorCode, message, details);
        } catch (Exception e) {
            return new HealthCheckResult(
                false, 
                "UNEXPECTED_ERROR", 
                "Unexpected error during Keycloak health check", 
                e.getClass().getSimpleName() + ": " + e.getMessage()
            );
        }
    }
    
    @Deprecated
    private boolean checkKeycloakHealth(AuthConfigService.AuthProvider provider) {
        return checkKeycloakHealthWithDetails(provider).isHealthy();
    }
    
    private HealthCheckResult checkAzureHealthWithDetails(AuthConfigService.AuthProvider provider) {
        try {
            String healthUrl = "https://login.microsoftonline.com/" + provider.getTenantId() + 
                              "/v2.0/.well-known/openid_configuration";
            String response = restTemplate.getForObject(healthUrl, String.class);
            
            if (response != null && response.contains("issuer")) {
                return new HealthCheckResult(
                    true, 
                    "SUCCESS", 
                    "Azure AD is accessible and responding correctly", 
                    "OpenID configuration endpoint returned valid response"
                );
            } else {
                return new HealthCheckResult(
                    false, 
                    "INVALID_RESPONSE", 
                    "Azure AD returned invalid OpenID configuration", 
                    "Response does not contain expected 'issuer' field"
                );
            }
            
        } catch (HttpClientErrorException e) {
            String errorCode = "HTTP_CLIENT_ERROR_" + e.getStatusCode().value();
            String message = "HTTP client error: " + e.getStatusCode() + " " + e.getStatusText();
            String details = "URL: https://login.microsoftonline.com/" + provider.getTenantId() + "/v2.0/.well-known/openid_configuration";
            
            if (e.getStatusCode().value() == 400) {
                message = "Invalid tenant ID - Azure AD rejected the request";
                details += ". Verify that the tenant ID is correct.";
            } else if (e.getStatusCode().value() == 404) {
                message = "Tenant not found - The specified Azure AD tenant does not exist";
                details += ". Verify that the tenant ID is correct.";
            }
            
            return new HealthCheckResult(false, errorCode, message, details);
        } catch (HttpServerErrorException e) {
            return new HealthCheckResult(
                false, 
                "HTTP_SERVER_ERROR_" + e.getStatusCode().value(), 
                "HTTP server error: " + e.getStatusCode() + " " + e.getStatusText(), 
                "Azure AD server is experiencing issues. Response: " + e.getResponseBodyAsString()
            );
        } catch (ResourceAccessException e) {
            String errorCode = "CONNECTION_ERROR";
            String message = "Connection error";
            String details = e.getMessage();
            
            if (e.getCause() instanceof ConnectException) {
                errorCode = "CONNECTION_REFUSED";
                message = "Connection refused - Azure AD is not reachable";
                details = "Cannot connect to Azure AD. Network issues may exist.";
            } else if (e.getCause() instanceof SocketTimeoutException) {
                errorCode = "CONNECTION_TIMEOUT";
                message = "Connection timeout - Azure AD is not responding";
                details = "Timeout after 10 seconds trying to connect to Azure AD";
            } else if (e.getCause() instanceof UnknownHostException) {
                errorCode = "UNKNOWN_HOST";
                message = "Unknown host - Cannot resolve Azure AD address";
                details = "DNS resolution failed for login.microsoftonline.com";
            }
            
            return new HealthCheckResult(false, errorCode, message, details);
        } catch (Exception e) {
            return new HealthCheckResult(
                false, 
                "UNEXPECTED_ERROR", 
                "Unexpected error during Azure AD health check", 
                e.getClass().getSimpleName() + ": " + e.getMessage()
            );
        }
    }
    
    @Deprecated
    private boolean checkAzureHealth(AuthConfigService.AuthProvider provider) {
        return checkAzureHealthWithDetails(provider).isHealthy();
    }
    
    public boolean isProviderHealthy(String providerId) {
        var provider = authConfigService.getProvider(providerId);
        return provider != null && provider.isHealthy();
    }
    
    public HealthCheckResult getLastHealthCheckResult(String providerId) {
        return lastHealthCheckResults.get(providerId);
    }
    
    public Map<String, HealthCheckResult> getAllHealthCheckResults() {
        return Map.copyOf(lastHealthCheckResults);
    }
    
    public static class HealthCheckResult {
        private final boolean healthy;
        private final String errorCode;
        private final String message;
        private final String details;
        private final LocalDateTime timestamp;
        
        public HealthCheckResult(boolean healthy, String errorCode, String message, String details) {
            this.healthy = healthy;
            this.errorCode = errorCode;
            this.message = message;
            this.details = details;
            this.timestamp = LocalDateTime.now();
        }
        
        public boolean isHealthy() { return healthy; }
        public String getErrorCode() { return errorCode; }
        public String getMessage() { return message; }
        public String getDetails() { return details; }
        public LocalDateTime getTimestamp() { return timestamp; }
        
        public String getFormattedTimestamp() {
            return timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        
        @Override
        public String toString() {
            return String.format("HealthCheckResult{healthy=%s, errorCode='%s', message='%s', details='%s', timestamp=%s}",
                               healthy, errorCode, message, details, getFormattedTimestamp());
        }
    }
}