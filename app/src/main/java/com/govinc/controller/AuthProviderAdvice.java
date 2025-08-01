package com.govinc.controller;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class AuthProviderAdvice {
    private final AvailableAuthProviders availableAuthProviders;

    public AuthProviderAdvice(AvailableAuthProviders availableAuthProviders) {
        this.availableAuthProviders = availableAuthProviders;
    }

    @ModelAttribute("oauthProviders")
    public java.util.Map<String, Boolean> listProviders() {
        java.util.Map<String, Boolean> map = new java.util.HashMap<>();
        map.put("keycloak", availableAuthProviders.isKeycloakAvailable());
        map.put("azure", availableAuthProviders.isAzureAvailable());
        // Add more providers if needed, e.g. map.put("google", ...);
        return map;
    }
}
