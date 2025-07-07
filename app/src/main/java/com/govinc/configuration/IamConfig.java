package com.govinc.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

@Configuration
@ConfigurationProperties(prefix = "spring.security.oauth2.client")
public class IamConfig {
    /**
     * AZURE, KEYCLOAK, MOCK (defaults to MOCK if missing/not set)
     */
    private String provider = "MOCK";
    // Azure fields
    private String azureClientId;
    private String azureClientSecret;
    private String azureTenantId;
    // Keycloak fields - using Spring Boot conventions
    private String registrationKeycloakClientId;
    private String registrationKeycloakClientSecret;
    private String providerKeycloakIssuerUri;

    private String loginPage; // new field for redirect login page like 'spring.security.oauth2.client.login-page'

    @PostConstruct
    public void debugPrintConfig() {
        System.out.println("---- Active IAM Provider: " + getProvider());
        System.out.println("spring.security.oauth2.client.registration.keycloak.client-id=" + getRegistrationKeycloakClientId());
        System.out.println("spring.security.oauth2.client.registration.keycloak.client-secret=" + (getRegistrationKeycloakClientSecret() != null && !getRegistrationKeycloakClientSecret().isEmpty() ? "***" : ""));
        System.out.println("spring.security.oauth2.client.provider.keycloak.issuer-uri=" + getProviderKeycloakIssuerUri());
        System.out.println("spring.security.oauth2.client.login-page=" + getLoginPage());
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

    public String getRegistrationKeycloakClientId() { return registrationKeycloakClientId; }
    public void setRegistrationKeycloakClientId(String registrationKeycloakClientId) { this.registrationKeycloakClientId = registrationKeycloakClientId; }
    public String getRegistrationKeycloakClientSecret() { return registrationKeycloakClientSecret; }
    public void setRegistrationKeycloakClientSecret(String registrationKeycloakClientSecret) { this.registrationKeycloakClientSecret = registrationKeycloakClientSecret; }
    public String getProviderKeycloakIssuerUri() { return providerKeycloakIssuerUri; }
    public void setProviderKeycloakIssuerUri(String providerKeycloakIssuerUri) { this.providerKeycloakIssuerUri = providerKeycloakIssuerUri; }

    public String getLoginPage() { return loginPage; }
    public void setLoginPage(String loginPage) { this.loginPage = loginPage; }
}
