package com.govinc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing dynamic authentication provider configuration.
 * This service handles OAuth2 providers (Keycloak, Azure AD) and form-based authentication.
 * 
 * All OAuth2 configurations are now managed dynamically through the web interface
 * and persisted to avoid conflicts with Spring Boot's OAuth2 auto-configuration.
 */
@Service
public class AuthConfigService {
    private static final Logger logger = LoggerFactory.getLogger(AuthConfigService.class);
    
    @Autowired
    private AuthProviderPersistenceService persistenceService;
    
    private final Map<String, AuthProvider> authProviders = new ConcurrentHashMap<>();

    /**
     * Initializes the authentication service by loading persisted providers
     * and ensuring form authentication is always available as fallback.
     */
    public void initialize() {
        logger.info("Initializing dynamic authentication providers...");
        
        // Load persisted providers from database/file storage
        Map<String, AuthProvider> persistedProviders = persistenceService.loadPersistedProviders();
        authProviders.putAll(persistedProviders);
        
        logger.info("Loaded {} persisted authentication providers", persistedProviders.size());
        
        // Always ensure form-based authentication is available as fallback
        AuthProvider inMemory = new AuthProvider(
            "form",
            "Local Login",
            null,
            null,
            null,
            null,
            null,
            AuthProviderType.FORM
        );
        authProviders.put("form", inMemory);
        logger.info("Form-based authentication configured as fallback");
        
        logger.info("Authentication initialization complete. Available providers: {} (Total: {}, Persisted: {}, Form: 1)", 
                   authProviders.keySet(), authProviders.size(), persistedProviders.size());
    }
    
    /**
     * Gets all available authentication providers
     */
    public Map<String, AuthProvider> getAvailableProviders() {
        return Map.copyOf(authProviders);
    }
    
    /**
     * Gets a specific authentication provider by ID
     */
    public AuthProvider getProvider(String providerId) {
        return authProviders.get(providerId);
    }
    
    /**
     * Checks if a provider is available and healthy
     */
    public boolean isProviderAvailable(String providerId) {
        AuthProvider provider = authProviders.get(providerId);
        return provider != null && provider.isHealthy() && provider.isConfigured();
    }
    
    /**
     * Adds or updates an authentication provider
     * This will automatically trigger OAuth2 configuration refresh
     */
    public void addOrUpdateProvider(AuthProvider provider) {
        authProviders.put(provider.getId(), provider);
        
        // Persist the provider configuration (except form auth)
        if (provider.getType() != AuthProviderType.FORM) {
            persistenceService.saveProvider(provider);
        }
        
        // Trigger OAuth2 client registration refresh
        refreshOAuth2Configuration();
        
        logger.info("Added/updated authentication provider: {} (Type: {})", 
                   provider.getId(), provider.getType());
    }
    
    /**
     * Removes an authentication provider (except form authentication)
     * This will automatically trigger OAuth2 configuration refresh
     */
    public void removeProvider(String providerId) {
        if (!"form".equals(providerId)) { // Never remove form authentication
            authProviders.remove(providerId);
            
            // Remove from persistence
            persistenceService.removeProvider(providerId);
            
            // Trigger OAuth2 client registration refresh
            refreshOAuth2Configuration();
            
            logger.info("Removed authentication provider: {}", providerId);
        } else {
            logger.warn("Cannot remove form authentication provider - it's the fallback option");
        }
    }
    
    /**
     * Checks if any OAuth2 providers are configured and available
     */
    public boolean hasOAuth2Providers() {
        return authProviders.values().stream()
            .anyMatch(p -> p.getType() != AuthProviderType.FORM && p.isConfigured());
    }
    
    /**
     * Refreshes OAuth2 client registrations after provider changes.
     * This method will be called by Spring's application context to update
     * the dynamic client registration repository.
     */
    private void refreshOAuth2Configuration() {
        try {
            // This will trigger a refresh of OAuth2 client registrations
            // The DynamicClientRegistrationRepository will pick up changes automatically
            logger.debug("OAuth2 configuration refresh triggered");
        } catch (Exception e) {
            logger.error("Failed to refresh OAuth2 configuration", e);
        }
    }
    
    /**
     * Gets a summary of all configured providers for admin interface
     */
    public Map<String, Object> getProviderSummary() {
        Map<String, Object> summary = new HashMap<>();
        
        Map<String, Object> providerDetails = new HashMap<>();
        for (var entry : authProviders.entrySet()) {
            var provider = entry.getValue();
            Map<String, Object> details = new HashMap<>();
            details.put("displayName", provider.getDisplayName());
            details.put("type", provider.getType().toString());
            details.put("configured", provider.isConfigured());
            details.put("healthy", provider.isHealthy());
            providerDetails.put(entry.getKey(), details);
        }
        
        summary.put("providers", providerDetails);
        summary.put("totalProviders", authProviders.size());
        summary.put("oauth2ProvidersAvailable", hasOAuth2Providers());
        summary.put("configuredProviders", 
                   authProviders.values().stream()
                       .mapToInt(p -> p.isConfigured() ? 1 : 0)
                       .sum());
        
        return summary;
    }
    
    /**
     * Authentication provider class representing different auth methods
     */
    public static class AuthProvider {
        private final String id;
        private final String displayName;
        private final String clientId;
        private final String clientSecret;
        private final String issuerUri;
        private final String tenantId;
        private final String redirectUri;
        private final AuthProviderType type;
        private boolean healthy = true;
        
        public AuthProvider(String id, String displayName, String clientId, 
                          String clientSecret, String issuerUri, String tenantId, 
                          AuthProviderType type) {
            this(id, displayName, clientId, clientSecret, issuerUri, tenantId, null, type);
        }
        
        public AuthProvider(String id, String displayName, String clientId, 
                          String clientSecret, String issuerUri, String tenantId, 
                          String redirectUri, AuthProviderType type) {
            this.id = id;
            this.displayName = displayName;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.issuerUri = issuerUri;
            this.tenantId = tenantId;
            this.redirectUri = redirectUri;
            this.type = type;
        }
        
        // Getters
        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public String getClientId() { return clientId; }
        public String getClientSecret() { return clientSecret; }
        public String getIssuerUri() { return issuerUri; }
        public String getTenantId() { return tenantId; }
        public String getRedirectUri() { return redirectUri; }
        public AuthProviderType getType() { return type; }
        public boolean isHealthy() { return healthy; }
        
        public void setHealthy(boolean healthy) { this.healthy = healthy; }
        
        /**
         * Checks if the provider has all required configuration parameters
         */
        public boolean isConfigured() {
            return switch (type) {
                case FORM -> true; // Form auth is always configured
                case KEYCLOAK -> clientId != null && !clientId.trim().isEmpty() &&
                               clientSecret != null && !clientSecret.trim().isEmpty() &&
                               issuerUri != null && !issuerUri.trim().isEmpty();
                case AZURE -> clientId != null && !clientId.trim().isEmpty() &&
                             clientSecret != null && !clientSecret.trim().isEmpty() &&
                             tenantId != null && !tenantId.trim().isEmpty();
            };
        }
        
        @Override
        public String toString() {
            return "AuthProvider{" +
                "id='" + id + '\'' +
                ", displayName='" + displayName + '\'' +
                ", type=" + type +
                ", configured=" + isConfigured() +
                ", healthy=" + healthy +
                '}';
        }
    }
    
    /**
     * Enumeration of supported authentication provider types
     */
    public enum AuthProviderType {
        FORM,     // Local form-based authentication with in-memory users
        KEYCLOAK, // Keycloak OAuth2/OIDC authentication
        AZURE     // Microsoft Azure AD OAuth2 authentication
    }
}