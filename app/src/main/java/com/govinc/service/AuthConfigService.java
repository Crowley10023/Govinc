package com.govinc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthConfigService {
    private static final Logger logger = LoggerFactory.getLogger(AuthConfigService.class);
    
    @Autowired
    private AuthProviderPersistenceService persistenceService;
    
    private final Map<String, AuthProvider> authProviders = new ConcurrentHashMap<>();
    
    @Value("${spring.security.oauth2.client.registration.keycloak.client-id:}")
    private String keycloakClientId;
    
    @Value("${spring.security.oauth2.client.registration.keycloak.client-secret:}")
    private String keycloakClientSecret;
    
    @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri:}")
    private String keycloakIssuerUri;
    
    @Value("${spring.security.oauth2.client.registration.azure.client-id:}")
    private String azureClientId;
    
    @Value("${spring.security.oauth2.client.registration.azure.client-secret:}")
    private String azureClientSecret;
    
    @Value("${spring.security.oauth2.client.provider.azure.tenant-id:}")
    private String azureTenantId;
    
    @Value("${spring.security.oauth2.client.registration.keycloak.redirect-uri:}")
    private String keycloakRedirectUri;
    
    @Value("${spring.security.oauth2.client.registration.azure.redirect-uri:}")
    private String azureRedirectUri;

    public void initialize() {
        logger.info("Initializing authentication providers...");
        
        // First, load persisted providers
        Map<String, AuthProvider> persistedProviders = persistenceService.loadPersistedProviders();
        authProviders.putAll(persistedProviders);
        
        // Then, initialize from configuration properties (properties override persisted)
        if (isConfigured(keycloakClientId, keycloakClientSecret)) {
            AuthProvider keycloak = new AuthProvider(
                "keycloak", 
                "Keycloak",
                keycloakClientId,
                keycloakClientSecret,
                keycloakIssuerUri,
                null,
                keycloakRedirectUri,
                AuthProviderType.KEYCLOAK
            );
            authProviders.put("keycloak", keycloak);
            logger.info("Keycloak provider configured from properties");
            
            // Save to persistence for next startup
            persistenceService.saveProvider(keycloak);
        }
        
        if (isConfigured(azureClientId, azureClientSecret)) {
            AuthProvider azure = new AuthProvider(
                "azure",
                "Microsoft Azure",
                azureClientId,
                azureClientSecret,
                null,
                azureTenantId,
                azureRedirectUri,
                AuthProviderType.AZURE
            );
            authProviders.put("azure", azure);
            logger.info("Azure provider configured from properties");
            
            // Save to persistence for next startup
            persistenceService.saveProvider(azure);
        }
        
        // Always ensure in-memory authentication is available
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
        logger.info("Form-based authentication configured");
        
        logger.info("Authentication initialization complete. Available providers: {} (Persisted: {})", 
                   authProviders.keySet(), persistedProviders.size());
    }
    
    private boolean isConfigured(String clientId, String clientSecret) {
        return clientId != null && !clientId.trim().isEmpty() && 
               clientSecret != null && !clientSecret.trim().isEmpty();
    }
    
    public Map<String, AuthProvider> getAvailableProviders() {
        return Map.copyOf(authProviders);
    }
    
    public AuthProvider getProvider(String providerId) {
        return authProviders.get(providerId);
    }
    
    public boolean isProviderAvailable(String providerId) {
        AuthProvider provider = authProviders.get(providerId);
        return provider != null && provider.isHealthy();
    }
    
    public void addOrUpdateProvider(AuthProvider provider) {
        authProviders.put(provider.getId(), provider);
        
        // Persist the provider configuration
        if (provider.getType() != AuthProviderType.FORM) {
            persistenceService.saveProvider(provider);
        }
        
        logger.info("Added/updated authentication provider: {}", provider.getId());
    }
    
    public void removeProvider(String providerId) {
        if (!"form".equals(providerId)) { // Never remove form authentication
            authProviders.remove(providerId);
            
            // Remove from persistence
            persistenceService.removeProvider(providerId);
            
            logger.info("Removed authentication provider: {}", providerId);
        } else {
            logger.warn("Cannot remove form authentication provider");
        }
    }
    
    public boolean hasOAuth2Providers() {
        return authProviders.values().stream()
            .anyMatch(p -> p.getType() != AuthProviderType.FORM);
    }
    
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
        
        public boolean isConfigured() {
            return switch (type) {
                case FORM -> true;
                case KEYCLOAK -> clientId != null && !clientId.trim().isEmpty() &&
                               clientSecret != null && !clientSecret.trim().isEmpty();
                case AZURE -> clientId != null && !clientId.trim().isEmpty() &&
                             clientSecret != null && !clientSecret.trim().isEmpty() &&
                             tenantId != null && !tenantId.trim().isEmpty();
            };
        }
    }
    
    public enum AuthProviderType {
        FORM, KEYCLOAK, AZURE
    }
}