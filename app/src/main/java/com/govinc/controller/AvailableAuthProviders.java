package com.govinc.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AvailableAuthProviders {
    @Value("${spring.security.oauth2.client.registration.keycloak.client-id:}")
    private String keycloakClientId;
    @Value("${spring.security.oauth2.client.registration.keycloak.client-secret:}")
    private String keycloakClientSecret;
    
    @Value("${spring.security.oauth2.client.registration.azure.client-id:}")
    private String azureClientId;
    @Value("${spring.security.oauth2.client.registration.azure.client-secret:}")
    private String azureClientSecret;

    public boolean isKeycloakAvailable() {
        return keycloakClientId != null && !keycloakClientId.trim().isEmpty()
            && keycloakClientSecret != null && !keycloakClientSecret.trim().isEmpty();
    }

    public boolean isAzureAvailable() {
        return azureClientId != null && !azureClientId.trim().isEmpty()
            && azureClientSecret != null && !azureClientSecret.trim().isEmpty();
    }
}
