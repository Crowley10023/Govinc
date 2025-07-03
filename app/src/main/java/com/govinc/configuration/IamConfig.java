package com.govinc.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "iam")
public class IamConfig {
    private String provider; // AZURE, KEYCLOAK, MOCK
    // Azure fields
    private String azureClientId;
    private String azureClientSecret;
    private String azureTenantId;
    // Keycloak fields
    private String keycloakIssuerUrl;
    private String keycloakRealm;
    private String keycloakClientId;
    private String keycloakClientSecret;
    // Getters and setters
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getAzureClientId() { return azureClientId; }
    public void setAzureClientId(String azureClientId) { this.azureClientId = azureClientId; }
    public String getAzureClientSecret() { return azureClientSecret; }
    public void setAzureClientSecret(String azureClientSecret) { this.azureClientSecret = azureClientSecret; }
    public String getAzureTenantId() { return azureTenantId; }
    public void setAzureTenantId(String azureTenantId) { this.azureTenantId = azureTenantId; }

    public String getKeycloakIssuerUrl() { return keycloakIssuerUrl; }
    public void setKeycloakIssuerUrl(String keycloakIssuerUrl) { this.keycloakIssuerUrl = keycloakIssuerUrl; }
    public String getKeycloakRealm() { return keycloakRealm; }
    public void setKeycloakRealm(String keycloakRealm) { this.keycloakRealm = keycloakRealm; }
    public String getKeycloakClientId() { return keycloakClientId; }
    public void setKeycloakClientId(String keycloakClientId) { this.keycloakClientId = keycloakClientId; }
    public String getKeycloakClientSecret() { return keycloakClientSecret; }
    public void setKeycloakClientSecret(String keycloakClientSecret) { this.keycloakClientSecret = keycloakClientSecret; }
}
