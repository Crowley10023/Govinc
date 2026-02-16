package com.govinc.controller;

import com.govinc.service.AuthConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

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
    public String login(HttpServletRequest request, Model model) {
        // Log session and cookie information
        String sessionId = request.getSession().getId();
        Cookie[] cookies = request.getCookies();

        logger.info("[OAUTH2-FLOW] GET /login - Session ID: {}", sessionId);
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("JSESSIONID".equals(cookie.getName())) {
                    logger.info("[OAUTH2-FLOW] JSESSIONID cookie found: {}, Secure: {}, HttpOnly: {}, Path: {}",
                        cookie.getValue(), cookie.getSecure(), cookie.isHttpOnly(), cookie.getPath());
                }
            }
        } else {
            logger.warn("[OAUTH2-FLOW] No cookies found in /login request");
        }

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

            logger.info("[OAUTH2-FLOW] Login page OAuth2 provider availability: Keycloak={}, Azure={}",
                oauthProviders.get("keycloak"), oauthProviders.get("azure"));
        } else {
            // If AuthConfigService is not available, assume no OAuth2 providers
            oauthProviders.put("keycloak", false);
            oauthProviders.put("azure", false);
            logger.warn("[OAUTH2-FLOW] AuthConfigService not available - OAuth2 providers disabled on login page");
        }

        model.addAttribute("oauthProviders", oauthProviders);

        // Check if any OAuth2 providers are available for display logic
        boolean hasOAuth2 = oauthProviders.values().stream().anyMatch(Boolean::booleanValue);
        model.addAttribute("hasOAuth2Providers", hasOAuth2);

        // Handle OAuth2 error messages
        String error = request.getParameter("error");
        String errorMessage = null;
        String errorDetails = null;

        if (error != null) {
            logger.info("[OAUTH2-FLOW] Login page error parameter: {}", error);
            
            switch (error) {
                case "invalid_secret":
                    errorMessage = "Azure Configuration Error";
                    errorDetails = (String) request.getSession().getAttribute("oauth2_error_details");
                    if (errorDetails == null) {
                        errorDetails = "The OAuth2 client secret is invalid or expired. Please verify your Azure app registration credentials and update them in the configuration.";
                    }
                    break;
                case "invalid_grant":
                    errorMessage = "Authorization Error";
                    errorDetails = (String) request.getSession().getAttribute("oauth2_error_details");
                    if (errorDetails == null) {
                        errorDetails = "The authorization code expired or is invalid. Please try logging in again.";
                    }
                    break;
                case "unauthorized_client":
                    errorMessage = "Application Not Authorized";
                    errorDetails = (String) request.getSession().getAttribute("oauth2_error_details");
                    if (errorDetails == null) {
                        errorDetails = "The application is not authorized in Azure. Please check the app registration settings.";
                    }
                    break;
                case "oauth2_error":
                    errorMessage = "OAuth2 Authentication Failed";
                    errorDetails = (String) request.getSession().getAttribute("oauth2_error_details");
                    if (errorDetails == null) {
                        errorDetails = "An authentication error occurred. Please check the Azure configuration and try again.";
                    }
                    break;
                default:
                    errorMessage = "Login Failed";
                    errorDetails = "Invalid username or password.";
                    break;
            }
            
            // Clear the error details from session after displaying
            request.getSession().removeAttribute("oauth2_error_details");
        }

        if (errorMessage != null) {
            model.addAttribute("errorMessage", errorMessage);
            model.addAttribute("errorDetails", errorDetails);
            logger.warn("[OAUTH2-FLOW] Displaying error on login page: {} - {}", errorMessage, errorDetails);
        }

        logger.info("[OAUTH2-FLOW] Rendering login page");
        return "login";
    }
}
