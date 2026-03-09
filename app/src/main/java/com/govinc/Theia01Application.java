package com.govinc;

import com.govinc.service.AuthConfigService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main Spring Boot application class for the Governance and Compliance tool.
 * 
 * This application provides:
 * - Security catalog and control management
 * - Compliance assessments and reporting  
 * - Dynamic authentication configuration (Form, Keycloak, Azure AD)
 * - Organization and user management
 */
@SpringBootApplication
@EnableScheduling
public class Theia01Application {
    private static final Logger logger = LoggerFactory.getLogger(Theia01Application.class);

    @Autowired(required = false)
    private AuthConfigService authConfigService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("\n========== Application Ready ==========");
        logger.info("Governance and Compliance Tool started successfully");
        
        // Display authentication configuration status
        if (authConfigService != null) {
            var summary = authConfigService.getProviderSummary();
            logger.info("Authentication Status:");
            logger.info("- Total Providers: {}", summary.get("totalProviders"));
            logger.info("- Configured Providers: {}", summary.get("configuredProviders"));
            logger.info("- OAuth2 Available: {}", summary.get("oauth2ProvidersAvailable"));
            
            var providers = authConfigService.getAvailableProviders();
            for (var provider : providers.values()) {
                logger.info("- {}: {} ({})", 
                    provider.getDisplayName(),
                    provider.isConfigured() ? "Configured" : "Not Configured",
                    provider.getType());
            }
        } else {
            logger.warn("AuthConfigService not available - authentication may not work properly");
        }
        
        logger.info("Admin interface available at: /admin/auth-config");
        logger.info("=====================================\n");
    }

    public static void main(String[] args) {
        SpringApplication.run(Theia01Application.class, args);
    }
}