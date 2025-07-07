package com.govinc.configuration;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URL;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IamStartupValidator {
    private final IamConfig iamConfig;
    private static final int TIMEOUT = 3000; // ms

    @Autowired
    public IamStartupValidator(IamConfig iamConfig) {
        this.iamConfig = iamConfig;
    }

    @PostConstruct
    public void validateIdentityProvider() {
        System.out.println("[IAM] IamStartupValidator INIT - starting identity provider availability verification...");
        String provider = iamConfig.getProvider();
        boolean ok = true;
        if ("KEYCLOAK".equalsIgnoreCase(provider)) {
            String issuerUri = iamConfig.getProviderKeycloakIssuerUri();
            System.out.println("[IAM] Checking Keycloak provider at: " + issuerUri);
            ok = checkHttpEndpoint(issuerUri + "/.well-known/openid-configuration");
        } else if ("AZURE".equalsIgnoreCase(provider)) {
            String tenant = iamConfig.getAzureTenantId();
            String endpoint = null;
            if (tenant != null && !tenant.isBlank()) {
                endpoint = "https://login.microsoftonline.com/" + tenant + "/v2.0/.well-known/openid-configuration";
                System.out.println("[IAM] Checking Azure provider at: " + endpoint);
                ok = checkHttpEndpoint(endpoint);
            } else {
                System.out.println("[IAM] Azure tenant ID not specified. Cannot check endpoint.");
                ok = false;
            }
        } else {
            System.out.println("[IAM] Provider is '" + provider + "'. No external verification needed.");
        }
        if (!ok && ("KEYCLOAK".equalsIgnoreCase(provider) || "AZURE".equalsIgnoreCase(provider))) {
            System.err.println("[IAM] Could not reach configured provider (" + provider + "). FALLING BACK TO MOCK provider.");
            iamConfig.setProvider("MOCK");
        } else if (ok && ("KEYCLOAK".equalsIgnoreCase(provider) || "AZURE".equalsIgnoreCase(provider))) {
            System.out.println("[IAM] Successfully validated external identity provider: " + provider);
        }
        System.out.println("[IAM] IamStartupValidator FINISHED - active provider: " + iamConfig.getProvider());
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
            System.out.println("[IAM] Endpoint '" + url + "' responded with status: " + code);
            return code >= 200 && code < 300;
        } catch (Exception e) {
            System.err.println("[IAM] Failed to contact IdP endpoint: " + url + ". Reason: " + e);
            return false;
        }
    }
}
