package com.govinc.configuration;

import com.govinc.service.AuthConfigService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Spring Security configuration that supports dynamic authentication providers.
 * 
 * This configuration:
 * - Always provides form-based authentication as a fallback
 * - Dynamically configures OAuth2 login when providers are available
 * - Loads users from application.properties for local authentication
 * - Excludes public endpoints from authentication
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
            "/login", // Allow login page
            "/admin/auth-config/**", // Allow auth config interface
            "/api/security-control/import/**", // Allow security control import API endpoints
            "/api/security-control/translate", // Allow security control translation API endpoint
            "/security-control/import", // Allow security control import form submission - CSRF exempt
            "/api/security-catalogs" // Allow catalog listing API endpoint
    };

    private static final Logger logger = Logger.getLogger(SecurityConfig.class.getName());

    @Autowired
    private CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;
    
    @Autowired(required = false)
    private AuthConfigService authConfigService;
    
    @Autowired(required = false)
    private ClientRegistrationRepository clientRegistrationRepository;
    
    // Load users from properties for form-based authentication
    @Value("#{${users:{:}}}")
    private Map<String, String> userProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(EXCLUDED_URLS).permitAll()
                .anyRequest().authenticated())
            .csrf(csrf -> csrf
                .ignoringRequestMatchers(EXCLUDED_URLS))
            .formLogin(form -> form
                .loginPage("/login")
                .permitAll()
                .successHandler(customAuthenticationSuccessHandler));
        
        // Configure OAuth2 login if OAuth2 providers are available
        // The dynamic client registration repository will handle provider availability
        try {
            if (authConfigService != null && authConfigService.hasOAuth2Providers() && clientRegistrationRepository != null) {
                http.oauth2Login(oauth2 -> oauth2
                    .loginPage("/login") // Redirects to our login page for OAuth
                    .successHandler(customAuthenticationSuccessHandler)
                    .failureHandler(oauth2AuthenticationFailureHandler())
                    // Use the dynamic client registration repository
                    .clientRegistrationRepository(clientRegistrationRepository));
                logger.info("OAuth2 login configured with " + (authConfigService.getAvailableProviders().size() - 1) + " OAuth2 providers"); // -1 for form auth
            } else {
                logger.info("No OAuth2 providers available - using form authentication only");
            }
        } catch (Exception e) {
            logger.warning("Failed to configure OAuth2 login - falling back to form authentication only: " + e.getMessage());
        }
        
        return http.build();
    }

    @Bean
    public AuthenticationFailureHandler oauth2AuthenticationFailureHandler() {
        return new SimpleUrlAuthenticationFailureHandler() {
            @Override
            public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                    org.springframework.security.core.AuthenticationException exception)
                    throws IOException, ServletException {
                //logger.severe("OAuth2 login failure for request from " + request.getRemoteAddr() + ": " + exception.getMessage());
                //logger.log(java.util.logging.Level.FINE, "OAuth2 login failure details", exception);
                super.onAuthenticationFailure(request, response, exception);
            }
        };
    }

    /**
     * Creates UserDetailsService with users loaded from application.properties
     * Format: users.<username>=<password>[,<email>]
     */
    @Bean
    public UserDetailsService userDetailsService() {
        List<org.springframework.security.core.userdetails.UserDetails> users = new ArrayList<>();
        
        // Add users from properties
        if (userProperties != null && !userProperties.isEmpty()) {
            for (Map.Entry<String, String> entry : userProperties.entrySet()) {
                String username = entry.getKey();
                String value = entry.getValue();
                
                // Parse password and optional email from value
                String[] parts = value.split(",");
                String password = parts[0].trim();
                
                users.add(User.withUsername(username)
                    .password(passwordEncoder().encode(password))
                    .roles("USER", "ADMIN") // All local users get admin role for now
                    .build());
                
                logger.fine("Loaded local user: " + username);
            }
        }
        
        // Always ensure at least one admin user exists
        if (users.isEmpty()) {
            users.add(User.withUsername("admin")
                .password(passwordEncoder().encode("admin"))
                .roles("ADMIN")
                .build());
            logger.warning("No users configured in properties, using default admin/admin");
        }
        
        logger.info("Configured " + users.size() + " local users for form-based authentication");
        return new InMemoryUserDetailsManager(users);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
