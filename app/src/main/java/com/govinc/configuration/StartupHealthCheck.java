package com.govinc.configuration;

import com.govinc.service.AuthConfigService;
import com.govinc.service.AuthProviderHealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StartupHealthCheck {
    private static final Logger logger = LoggerFactory.getLogger(StartupHealthCheck.class);
    
    @Autowired
    private AuthConfigService authConfigService;
    
    @Autowired
    private AuthProviderHealthService healthService;
    
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("Application startup completed successfully");
        
        var providers = authConfigService.getAvailableProviders();
        logger.info("Authentication providers configured: {}", providers.size());
        
        for (var entry : providers.entrySet()) {
            var provider = entry.getValue();
            logger.info("Provider '{}' ({}): configured={}, healthy={}", 
                       provider.getId(), 
                       provider.getDisplayName(),
                       provider.isConfigured(),
                       provider.isHealthy());
        }
        
        if (authConfigService.hasOAuth2Providers()) {
            logger.info("OAuth2 authentication is available");
        } else {
            logger.info("Only form-based authentication is available");
        }
        
        logger.info("Authentication configuration can be managed at: /admin/auth-config");
        
        // Perform initial health checks in the background
        try {
            healthService.checkAllProvidersHealth();
        } catch (Exception e) {
            logger.warn("Initial health check failed, but application will continue: {}", e.getMessage());
        }
    }
}