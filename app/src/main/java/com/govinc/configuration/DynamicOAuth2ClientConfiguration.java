package com.govinc.configuration;

import com.govinc.service.AuthConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class DynamicOAuth2ClientConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(DynamicOAuth2ClientConfiguration.class);
    
    @Autowired
    private AuthConfigService authConfigService;
    
    @PostConstruct
    public void init() {
        authConfigService.initialize();
    }
    
    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        List<ClientRegistration> registrations = new ArrayList<>();
        
        var providers = authConfigService.getAvailableProviders();
        
        for (var provider : providers.values()) {
            if (provider.getType() == AuthConfigService.AuthProviderType.FORM || !provider.isConfigured()) {
                continue;
            }
            
            try {
                ClientRegistration registration = createClientRegistration(provider);
                if (registration != null) {
                    registrations.add(registration);
                    logger.info("Registered OAuth2 client for provider: {}", provider.getId());
                }
            } catch (Exception e) {
                logger.error("Failed to register OAuth2 client for provider {}: {}", 
                           provider.getId(), e.getMessage());
                provider.setHealthy(false);
            }
        }
        
        if (registrations.isEmpty()) {
            logger.info("No OAuth2 providers configured - using empty client registration repository");
            // Return empty repository - this prevents OAuth2 auto-configuration issues
            return new InMemoryClientRegistrationRepository();
        }
        
        logger.info("Created OAuth2 client registration repository with {} providers", registrations.size());
        return new InMemoryClientRegistrationRepository(registrations);
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
        
        return ClientRegistration.withRegistrationId("keycloak")
            .clientId(provider.getClientId())
            .clientSecret(provider.getClientSecret())
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
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
        
        return ClientRegistration.withRegistrationId("azure")
            .clientId(provider.getClientId())
            .clientSecret(provider.getClientSecret())
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid", "profile", "email", "offline_access")
            .authorizationUri("https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/authorize")
            .tokenUri("https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token")
            .jwkSetUri("https://login.microsoftonline.com/" + tenantId + "/discovery/v2.0/keys")
            .issuerUri("https://login.microsoftonline.com/" + tenantId + "/v2.0")
            .userNameAttributeName("sub")
            .clientName("Microsoft")
            .build();
    }
}