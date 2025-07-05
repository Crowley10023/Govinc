package com.govinc.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

@Configuration
@ConfigurationProperties(prefix = "iam")
public class IamConfig {
    /**
     * AZURE, KEYCLOAK, MOCK (defaults to MOCK if missing/not set)
     */
    private String provider = "MOCK";
    // Azure fields
    private String azureClientId;
    private String azureClientSecret;
    private String azureTenantId;
    // Keycloak fields
    private String keycloakIssuerUrl;
    private String keycloakRealm;
    private String keycloakClientId;
    private String keycloakClientSecret;

    @PostConstruct
    public void debugPrintConfig() {
        System.out.println("---- Active IAM Provider: " + getProvider());
        System.out.println("iam.provider=" + getProvider());
        System.out.println("iam.azure-client-id=" + getAzureClientId());
        System.out.println("iam.azure-client-secret=" + (getAzureClientSecret() != null && !getAzureClientSecret().isEmpty() ? "***" : ""));
        System.out.println("iam.azure-tenant-id=" + getAzureTenantId());
        System.out.println("iam.keycloak-issuer-url=" + getKeycloakIssuerUrl());
        System.out.println("iam.keycloak-realm=" + getKeycloakRealm());
        System.out.println("iam.keycloak-client-id=" + getKeycloakClientId());
        System.out.println("iam.keycloak-client-secret=" + (getKeycloakClientSecret() != null && !getKeycloakClientSecret().isEmpty() ? "***" : ""));
        System.out.println("----");
    }

    public String getProvider() {
        return (provider == null || provider.isBlank()) ? "MOCK" : provider;
    }
    public void setProvider(String provider) {
        this.provider = provider;
    }

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
