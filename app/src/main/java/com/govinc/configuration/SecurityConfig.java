package com.govinc.configuration;

import com.govinc.service.AuthConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Spring Security configuration that supports dynamic authentication providers.
 * 
 * This configuration:
 * - Always provides form-based authentication as a fallback
 * - Dynamically configures OAuth2 login when providers are available
 * - Loads users from application.properties for local authentication
 * - Excludes public endpoints from authentication
 * - Requires ADMIN role for /admin/** endpoints
 */
@Configuration
public class SecurityConfig {
    // URLs to exclude from authentication (publicly accessible endpoints)
    private static final String[] EXCLUDED_URLS = {
            "/assessment-direct/*/alldata",
            "/assessment-direct/*/data",
            "/assessment-direct/*/answer",
            "/assessment-direct/*/control/*/comment",
            "/assessment-direct.html",
            "/assessment-direct/*",
            "/assessment/*/answer",
            "/static/**",
            "/favicon.ico",
            "style.css",
            "/style.css",
            "/general.css",            
            "/config/openai/suggest-theme",
            "/theme-css",
            "/config/layout/**",
            "/layoutConfig/**",
            "/config/layout",
            "/layoutConfig",
            "/config/image-upload/preview", // Allow logo preview access without authentication
            "/title.png", // Allow default logo access without authentication
            "/login", // Allow login page
            "/oauth2",
            "/login/oauth2",
            "/api/security-control/import/**", // Allow security control import API endpoints
            "/api/security-control/translate", // Allow security control translation API endpoint
            "/security-control/import", // Allow security control import form submission - CSRF exempt
            "/api/security-catalogs" // Allow catalog listing API endpoint
    };

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Autowired
    private CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;
    
    @Autowired(required = false)
    private AuthConfigService authConfigService;
    
    @Autowired(required = false)
    private ClientRegistrationRepository clientRegistrationRepository;
    
    @Autowired
    private Environment environment;
    
    // Load users from properties for form-based authentication
    @Value("#{${users:{:}}}")
    private Map<String, String> userProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(EXCLUDED_URLS).permitAll()
                .requestMatchers("/admin/**").hasAnyRole("ADMIN")
                .anyRequest().authenticated())
            .csrf(csrf -> csrf
                .ignoringRequestMatchers(EXCLUDED_URLS))
            .addFilterBefore(new OAuth2AuthorizationRequestLoggingFilter(), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new OAuth2DebugLoggingFilter(), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
            .formLogin(form -> form
                .loginPage("/login")
                .permitAll()
                .successHandler(customAuthenticationSuccessHandler)
                .failureUrl("/login?error=form_login_failed"));

        // Configure OAuth2 login if OAuth2 providers are available
        // Spring Security 6.x automatically uses HttpSessionOAuth2AuthorizationRequestRepository for session-based storage
        // The dynamic client registration repository will handle provider availability
        try {
            if (authConfigService != null && authConfigService.hasOAuth2Providers() && clientRegistrationRepository != null) {
                logger.info("[OAUTH2-FLOW] Configuring OAuth2 login with dynamic client registration");
                http.oauth2Login(oauth2 -> oauth2
                    .loginPage("/login") // Redirects to our login page for OAuth
                    .successHandler(customAuthenticationSuccessHandler)
                    .failureHandler(oauth2AuthenticationFailureHandler())
                    // Use the dynamic client registration repository
                    .clientRegistrationRepository(clientRegistrationRepository));
                logger.info("[OAUTH2-FLOW] OAuth2 login configured with " + (authConfigService.getAvailableProviders().size() - 1) + " OAuth2 providers"); // -1 for form auth
            } else {
                logger.info("[OAUTH2-FLOW] No OAuth2 providers available - using form authentication only");
            }
        } catch (Exception e) {
            logger.warn("[OAUTH2-FLOW] Failed to configure OAuth2 login - falling back to form authentication only: " + e.getMessage());
        }

        return http.build();
    }

    /**
     * Debug logging filter to trace OAuth2 flow across requests
     */
    private static class OAuth2DebugLoggingFilter extends org.springframework.web.filter.OncePerRequestFilter {
        private static final Logger filterLogger = LoggerFactory.getLogger("OAuth2DebugLoggingFilter");

        @Override
        protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                       jakarta.servlet.http.HttpServletResponse response,
                                       jakarta.servlet.FilterChain filterChain)
                throws jakarta.servlet.ServletException, java.io.IOException {
            String path = request.getRequestURI();
            String queryString = request.getQueryString();
            String sessionId = request.getSession().getId();

            // Log OAuth2-related paths
            if (path.contains("/oauth2") || path.contains("/login")) {
                filterLogger.info("[OAUTH2-FLOW] " + request.getMethod() + " " + path +
                    (queryString != null ? "?" + queryString : "") +
                    " | SessionID: " + sessionId);

                // Log cookies
                jakarta.servlet.http.Cookie[] cookies = request.getCookies();
                if (cookies != null) {
                    for (jakarta.servlet.http.Cookie cookie : cookies) {
                        if (cookie.getName().equals("JSESSIONID")) {
                            filterLogger.info("[OAUTH2-FLOW] JSESSIONID Cookie: " + cookie.getValue() +
                                " | Path: " + cookie.getPath() + " | Domain: " + cookie.getDomain() +
                                " | Secure: " + cookie.getSecure() + " | HttpOnly: " + cookie.isHttpOnly());
                        }
                    }
                } else {
                    filterLogger.warn("[OAUTH2-FLOW] No cookies in request to " + path);
                }

                // Log relevant headers
                filterLogger.info("[OAUTH2-FLOW] Headers - Referer: " + request.getHeader("Referer") +
                    " | Host: " + request.getHeader("Host") +
                    " | X-Forwarded-Proto: " + request.getHeader("X-Forwarded-Proto") +
                    " | X-Forwarded-Host: " + request.getHeader("X-Forwarded-Host"));
            }

            filterChain.doFilter(request, response);
        }
    }

    @Bean
    public AuthenticationFailureHandler oauth2AuthenticationFailureHandler() {
        return new OAuth2ErrorHandlingFailureHandler();
    }

    /**
     * Custom OAuth2 failure handler that detects specific error types and provides user-friendly messages.
     * Stores error details in session and redirects to login page.
     */
    private static class OAuth2ErrorHandlingFailureHandler implements AuthenticationFailureHandler {
        private static final Logger handlerLogger = LoggerFactory.getLogger(OAuth2ErrorHandlingFailureHandler.class);

        @Override
        public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                org.springframework.security.core.AuthenticationException exception)
                throws IOException, ServletException {
            handlerLogger.error("[OAUTH2-FLOW] OAuth2 login failure for request from " + request.getRemoteAddr() + ": " + exception.getMessage());
            handlerLogger.debug("[OAUTH2-FLOW] OAuth2 login failure details", exception);

            // Detect specific error types and provide user-friendly messages
            String errorType = "oauth2_error";
            String errorDetails = null;

            String exceptionMessage = exception.getMessage();
            if (exceptionMessage != null) {
                if (exceptionMessage.contains("invalid_client") || exceptionMessage.contains("Client authentication failed")) {
                    errorType = "invalid_secret";
                    errorDetails = "The OAuth2 client secret is invalid or expired. Please update the Azure configuration.";
                    handlerLogger.warn("[OAUTH2-FLOW] Detected invalid client secret error");
                } else if (exceptionMessage.contains("invalid_grant")) {
                    errorType = "invalid_grant";
                    errorDetails = "Authorization code is invalid or expired. Please try logging in again.";
                    handlerLogger.warn("[OAUTH2-FLOW] Detected invalid grant error");
                } else if (exceptionMessage.contains("unauthorized_client")) {
                    errorType = "unauthorized_client";
                    errorDetails = "The application is not authorized. Please check the Azure app registration.";
                    handlerLogger.warn("[OAUTH2-FLOW] Detected unauthorized client error");
                }
            }

            // Store error details in session for display on login page
            if (errorDetails != null) {
                request.getSession().setAttribute("oauth2_error_details", errorDetails);
                handlerLogger.info("[OAUTH2-FLOW] Stored error details in session: {}", errorDetails);
            }
            
            // Redirect to login page with error type parameter
            String redirectUrl = "/login?error=" + errorType;
            handlerLogger.info("[OAUTH2-FLOW] Redirecting to: {}", redirectUrl);
            response.sendRedirect(request.getContextPath() + redirectUrl);
        }
    }

    /**
     * Creates UserDetailsService with users loaded from application.properties
     * Format: users.<username>=<password> or users.<username>=<password>,ADMIN or users.<username>=<password>,USER,ADMIN
     * 
     * Examples:
     *   users.admin=password123        (will get ADMIN role by default)
     *   users.john=pass123,ADMIN       (explicitly set as ADMIN)
     *   users.jane=pass456,USER        (set as USER only, no admin access)
     *   users.bob=pass789,USER,ADMIN   (set with multiple roles)
     */
    @Bean
    public UserDetailsService userDetailsService() {
        List<org.springframework.security.core.userdetails.UserDetails> users = new ArrayList<>();
        
        // Add users from properties
        if (userProperties != null && !userProperties.isEmpty()) {
            for (Map.Entry<String, String> entry : userProperties.entrySet()) {
                String username = entry.getKey();
                String value = entry.getValue();
                
                // Parse password and optional roles from value (format: "password[,ROLE1,ROLE2]")
                String[] parts = value.split(",", 2); // Split on first comma only
                String password = parts[0].trim();
                
                var userBuilder = User.withUsername(username)
                    .password(passwordEncoder().encode(password));
                
                // Parse roles if specified (format: "password,ROLE1,ROLE2,...")
                if (parts.length > 1) {
                    String[] roles = parts[1].split(",");
                    for (String role : roles) {
                        userBuilder.roles(role.trim());
                    }
                } else {
                    // Default to ADMIN role for backward compatibility
                    userBuilder.roles("ADMIN");
                }
                
                users.add(userBuilder.build());
                logger.debug("Loaded local user: " + username);
            }
        }
        
        // Always ensure at least one admin user exists for fallback (unless in production mode)
        if (users.isEmpty()) {
            boolean isProduction = "true".equalsIgnoreCase(environment.getProperty("app.production", "false"));
            if (!isProduction) {
                users.add(User.withUsername("admin")
                    .password(passwordEncoder().encode("admin"))
                    .roles("ADMIN")
                    .build());
                logger.warn("No users configured in properties, using default admin/admin. Configure users in application.properties or database.");
            } else {
                logger.warn("Production environment with no configured users. Admin access is disabled.");
            }
        }
        
        logger.info("Configured " + users.size() + " local users for form-based authentication");
        return new InMemoryUserDetailsManager(users);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
