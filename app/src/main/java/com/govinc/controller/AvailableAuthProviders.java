package com.govinc.controller;

import com.govinc.service.AuthConfigService;
import com.govinc.service.AuthProviderHealthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AvailableAuthProviders {
    
    @Autowired
    private AuthConfigService authConfigService;
    
    @Autowired
    private AuthProviderHealthService healthService;

    public boolean isKeycloakAvailable() {
        return authConfigService.isProviderAvailable("keycloak") && 
               healthService.isProviderHealthy("keycloak");
    }

    public boolean isAzureAvailable() {
        return authConfigService.isProviderAvailable("azure") && 
               healthService.isProviderHealthy("azure");
    }
    
    public boolean isFormAuthAvailable() {
        return authConfigService.isProviderAvailable("form");
    }
}