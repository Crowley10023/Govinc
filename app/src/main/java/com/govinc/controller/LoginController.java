package com.govinc.controller;

import com.govinc.service.AuthConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for handling login page and dynamic OAuth2 provider display.
 * 
 * This controller populates the login page with available authentication options
 * based on the current dynamic configuration.
 */
@Controller
public class LoginController {
    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);
    
    @Autowired(required = false)
    private AuthConfigService authConfigService;
    
    @GetMapping("/login")
    public String login(Model model) {
        // Prepare OAuth2 provider availability for the login page
        Map<String, Boolean> oauthProviders = new HashMap<>();
        
        if (authConfigService != null) {
            var providers = authConfigService.getAvailableProviders();
            
            // Check each OAuth2 provider type
            oauthProviders.put("keycloak", 
                providers.containsKey("keycloak") && 
                providers.get("keycloak").isConfigured() && 
                providers.get("keycloak").isHealthy());
                
            oauthProviders.put("azure", 
                providers.containsKey("azure") && 
                providers.get("azure").isConfigured() && 
                providers.get("azure").isHealthy());
                
            logger.debug("Login page OAuth2 provider availability: {}", oauthProviders);
        } else {
            // If AuthConfigService is not available, assume no OAuth2 providers
            oauthProviders.put("keycloak", false);
            oauthProviders.put("azure", false);
            logger.warn("AuthConfigService not available - OAuth2 providers disabled on login page");
        }
        
        model.addAttribute("oauthProviders", oauthProviders);
        
        // Check if any OAuth2 providers are available for display logic
        boolean hasOAuth2 = oauthProviders.values().stream().anyMatch(Boolean::booleanValue);
        model.addAttribute("hasOAuth2Providers", hasOAuth2);
        
        return "login";
    }
}