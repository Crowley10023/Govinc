package com.govinc.configuration;

import com.govinc.service.AuthConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic OAuth2 Client Configuration that supports runtime changes
 * to authentication providers without requiring application restart.
 * 
 * This configuration creates a custom ClientRegistrationRepository that
 * can be updated dynamically when authentication providers are added,
 * modified, or removed through the web interface.
 */
@Configuration
public class DynamicOAuth2ClientConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(DynamicOAuth2ClientConfiguration.class);
    
    @Autowired
    private AuthConfigService authConfigService;
    
    private DynamicClientRegistrationRepository dynamicRepository;
    
    @PostConstruct
    public void init() {
        logger.info("Initializing dynamic OAuth2 configuration...");
        authConfigService.initialize();
        logger.info("Dynamic OAuth2 configuration initialized");
    }
    
    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        dynamicRepository = new DynamicClientRegistrationRepository(authConfigService);
        logger.info("Created dynamic client registration repository");
        return dynamicRepository;
    }
    
    /**
     * Custom ClientRegistrationRepository that can be updated at runtime
     */
    public static class DynamicClientRegistrationRepository implements ClientRegistrationRepository {
        private final AuthConfigService authConfigService;
        private final Map<String, ClientRegistration> registrations = new ConcurrentHashMap<>();
        private final Logger logger = LoggerFactory.getLogger(DynamicClientRegistrationRepository.class);
        
        public DynamicClientRegistrationRepository(AuthConfigService authConfigService) {
            this.authConfigService = authConfigService;
            refreshRegistrations();
        }
        
        @Override
        public ClientRegistration findByRegistrationId(String registrationId) {
            // Always refresh registrations to get latest configuration
            refreshRegistrations();
            
            ClientRegistration registration = registrations.get(registrationId);
            if (registration != null) {
                logger.debug("Found client registration for: {}", registrationId);
            } else {
                logger.debug("No client registration found for: {}", registrationId);
            }
            return registration;
        }
        
        /**
         * Refreshes all client registrations from current provider configuration
         */
        public synchronized void refreshRegistrations() {
            try {
                registrations.clear();
                
                var providers = authConfigService.getAvailableProviders();
                int configuredCount = 0;
                
                for (var provider : providers.values()) {
                    if (provider.getType() == AuthConfigService.AuthProviderType.FORM || !provider.isConfigured()) {
                        continue;
                    }
                    
                    try {
                        ClientRegistration registration = createClientRegistration(provider);
                        if (registration != null) {
                            registrations.put(provider.getId(), registration);
                            configuredCount++;
                            logger.debug("Refreshed OAuth2 client registration for: {}", provider.getId());
                        }
                    } catch (Exception e) {
                        logger.error("Failed to create client registration for provider {}: {}", 
                                   provider.getId(), e.getMessage());
                        provider.setHealthy(false);
                    }
                }
                
                logger.info("Refreshed client registrations: {} OAuth2 providers available", configuredCount);
                
            } catch (Exception e) {
                logger.error("Failed to refresh client registrations", e);
            }
        }
        
        private ClientRegistration createClientRegistration(AuthConfigService.AuthProvider provider) {
            return switch (provider.getType()) {
                case KEYCLOAK -> createKeycloakRegistration(provider);
                case AZURE -> createAzureRegistration(provider);
                case FORM -> null;
            };
        }
        
        private ClientRegistration createKeycloakRegistration(AuthConfigService.AuthProvider provider) {
            if (provider.getIssuerUri() == null || provider.getIssuerUri().trim().isEmpty()) {
                logger.warn("Keycloak issuer URI not configured - skipping registration");
                return null;
            }
            
            String redirectUri = provider.getRedirectUri() != null && !provider.getRedirectUri().trim().isEmpty()
                ? provider.getRedirectUri()
                : "{baseUrl}/login/oauth2/code/{registrationId}";
            
            return ClientRegistration.withRegistrationId("keycloak")
                .clientId(provider.getClientId())
                .clientSecret(provider.getClientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri)
                .scope("openid", "profile", "email")
                .authorizationUri(provider.getIssuerUri() + "/protocol/openid-connect/auth")
                .tokenUri(provider.getIssuerUri() + "/protocol/openid-connect/token")
                .userInfoUri(provider.getIssuerUri() + "/protocol/openid-connect/userinfo")
                .jwkSetUri(provider.getIssuerUri() + "/protocol/openid-connect/certs")
                .issuerUri(provider.getIssuerUri())
                .userNameAttributeName("preferred_username")
                .clientName("Keycloak")
                .build();
        }
        
        private ClientRegistration createAzureRegistration(AuthConfigService.AuthProvider provider) {
            if (provider.getTenantId() == null || provider.getTenantId().trim().isEmpty()) {
                logger.warn("Azure tenant ID not configured - skipping registration");
                return null;
            }
            
            String tenantId = provider.getTenantId();
            String redirectUri = provider.getRedirectUri() != null && !provider.getRedirectUri().trim().isEmpty()
                ? provider.getRedirectUri()
                : "{baseUrl}/login/oauth2/code/{registrationId}";
            
            return ClientRegistration.withRegistrationId("azure")
                .clientId(provider.getClientId())
                .clientSecret(provider.getClientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri)
                .scope("openid", "profile", "email", "offline_access")
                .authorizationUri("https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/authorize")
                .tokenUri("https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token")
                .jwkSetUri("https://login.microsoftonline.com/" + tenantId + "/discovery/v2.0/keys")
                .issuerUri("https://login.microsoftonline.com/" + tenantId + "/v2.0")
                .userNameAttributeName("sub")
                .clientName("Microsoft")
                .build();
        }
        
        /**
         * Returns all currently available registrations
         */
        public Map<String, ClientRegistration> getAllRegistrations() {
            refreshRegistrations();
            return Map.copyOf(registrations);
        }
    }
}