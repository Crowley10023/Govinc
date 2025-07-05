package com.govinc.configuration;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URL;

@Component
public class IamStartupValidator {
    private final IamConfig iamConfig;
    private static final int TIMEOUT = 3000; // ms

    @Autowired
    public IamStartupValidator(IamConfig iamConfig) {
        this.iamConfig = iamConfig;
    }

    @PostConstruct
    public void validateIdentityProvider() {
        String provider = iamConfig.getProvider();
        boolean ok = true;
        if ("KEYCLOAK".equalsIgnoreCase(provider)) {
            String issuerUrl = iamConfig.getKeycloakIssuerUrl();
            ok = checkHttpEndpoint(issuerUrl + "/.well-known/openid-configuration");
        } else if ("AZURE".equalsIgnoreCase(provider)) {
            String tenant = iamConfig.getAzureTenantId();
            if (tenant != null && !tenant.isBlank()) {
                String endpoint = "https://login.microsoftonline.com/" + tenant + "/v2.0/.well-known/openid-configuration";
                ok = checkHttpEndpoint(endpoint);
            } else {
                ok = false;
            }
        }
        if (!ok) {
            System.err.println("[IAM] Could not reach configured provider ("+provider+"). Falling back to MOCK provider.");
            iamConfig.setProvider("MOCK");
        } else {
            System.out.println("[IAM] Successfully validated external identity provider: " + provider);
        }
    }

    private boolean checkHttpEndpoint(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(TIMEOUT);
            connection.setReadTimeout(TIMEOUT);
            connection.setRequestMethod("GET");
            connection.connect();
            int code = connection.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception e) {
            System.err.println("[IAM] Failed to contact IdP endpoint: " + url + ". Reason: " + e);
            return false;
        }
    }
}
