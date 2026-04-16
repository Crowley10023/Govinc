package com.govinc.configuration;

import com.govinc.service.AuthConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
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
 * This configuration implements the security architecture requirements:
 *  - Only explicitly allowed public endpoints are permitAll (minimal surface)
 *  - All backend endpoints require authentication and, where applicable, authorization
 *  - CSRF protection is enabled for all non-exempt endpoints; only the anonymous
 *    assessment-direct write endpoints are exempt (they are intentionally obfuscated)
 *  - /config/** and /admin/** are restricted to ADMIN role
 */
@Configuration
public class SecurityConfig {
    // Minimal public URL surface per security architecture
    private static final String[] PUBLIC_URLS = {
            // Public (anonymous) assessment direct endpoints (read-only summaries and full data)
            "/assessment-direct/*/alldata",
            "/assessment-direct/*/data",
            "/assessment-direct.html",
            "/assessment-direct/*",
            // Public (anonymous) assessment direct write endpoints (obfuscated URLs) - must be permitAll
            "/assessment-direct/*/answer",
            "/assessment-direct/*/control/*/comment",
            "/assessment-direct/*/finalize",
            // Public AI guide endpoints for assessment-direct
            "/assessment-direct/guide/questions",
            "/assessment-direct/guide/answer",
            "/assessment-direct/guide/summary",

            // Static assets and theme
            "/static/**",
            "/favicon.ico",
            "/title.png",
            "/theme-css",
            "/style.css",
            "/general.css",
            "/config/image-upload/preview",

            // Login and OAuth endpoints must be reachable without prior authentication
            "/login",
            "/oauth2/**",
            "/login/oauth2/**",
            
            // Access denied page
            "/not-authorized"
            };

    // Only the anonymous assessment-direct write endpoints are exempt from CSRF
    // because they are used without authentication. All other state-changing
    // endpoints must require a CSRF token.
    private static final String[] CSRF_IGNORED_URLS = {
            "/assessment-direct/*/answer",
            "/assessment-direct/*/control/*/comment",
            "/assessment-direct/*/finalize",
            "/assessment-direct/guide/questions",
            "/assessment-direct/guide/answer",
            "/assessment-direct/guide/summary"
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

    @Autowired
    private com.govinc.authorization.AuthorizationService authorizationService;

    // Filter that augments authorities in the SecurityContext using app DB roles
    @Autowired
    private com.govinc.configuration.GrantedAuthoritiesAugmentationFilter grantedAuthoritiesAugmentationFilter;

    // Load users from properties for form-based authentication
    @Value("#{${users:{:}}}")
    private Map<String, String> userProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_URLS).permitAll()
                // CONFIG and admin areas must be ADMIN only per security architecture
                .requestMatchers("/config/**").hasRole("ADMIN")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // Security framework and management pages accessible to ADMIN and Information Security Manager
                .requestMatchers(
                    "/security-catalog/**",
                    "/security-control/**",
                    "/security-control-domain/**",
                    "/security-capability/**",
                    "/maturitymodel/**",
                    "/maturityanswer/**",
                    "/compliance/**",
                    "/reporting/**",
                    "/capability-report/**",
                    "/orgservice-assessment/**",
                    "/orgservices/**",
                    "/orgunits/**",
                    "/users/**",
                    "/statistics/**",
                    "/governance/**"
                ).hasAnyRole("ADMIN", "INFORMATION_SECURITY_MANAGER")
                // All other requests require authentication; fine-grained checks are performed in controllers
                .anyRequest().authenticated())
            // Required for same-origin modal iframe usage (e.g., org unit edit popup).
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            // Enable CSRF but ignore assessment-direct anonymous write endpoints only
            .csrf(csrf -> csrf.ignoringRequestMatchers(CSRF_IGNORED_URLS))
            .exceptionHandling(exception -> exception
                .accessDeniedHandler(accessDeniedHandler()))
            // Ensure the granted-authorities augmentation filter runs early so hasRole checks see DB-driven roles
            .addFilterBefore(grantedAuthoritiesAugmentationFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new OAuth2AuthorizationRequestLoggingFilter(), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new OAuth2DebugLoggingFilter(), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
            .formLogin(form -> form
                .loginPage("/login")
                .permitAll()
                .successHandler(customAuthenticationSuccessHandler)
                .failureUrl("/login?error=form_login_failed"));

        // Configure OAuth2 login if OAuth2 providers are available
        try {
            if (authConfigService != null && authConfigService.hasOAuth2Providers() && clientRegistrationRepository != null) {
                logger.info("[OAUTH2-FLOW] Configuring OAuth2 login with dynamic client registration");
                http.oauth2Login(oauth2 -> oauth2
                    .loginPage("/login")
                    .successHandler(customAuthenticationSuccessHandler)
                    .failureHandler(oauth2AuthenticationFailureHandler())
                    .clientRegistrationRepository(clientRegistrationRepository));
                logger.info("[OAUTH2-FLOW] OAuth2 login configured with " + (authConfigService.getAvailableProviders().size() - 1) + " OAuth2 providers");
            } else {
                logger.info("[OAUTH2-FLOW] No OAuth2 providers available - using form authentication only");
            }
        } catch (Exception e) {
            logger.warn("[OAUTH2-FLOW] Failed to configure OAuth2 login - falling back to form authentication only: " + e.getMessage());
        }

        return http.build();
    }

    /**
     * Custom access denied handler that displays a not-authorized page
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return new SecurityAccessDeniedHandler();
    }

    /**
     * Implementation of AccessDeniedHandler that displays the not-authorized view
     */
    private static class SecurityAccessDeniedHandler implements AccessDeniedHandler {
        private static final Logger accessDeniedLogger = LoggerFactory.getLogger("SecurityAccessDeniedHandler");

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response,
                org.springframework.security.access.AccessDeniedException accessDeniedException)
                throws IOException, ServletException {
            String username = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonymous";
            accessDeniedLogger.warn("Access denied for user: {} | Path: {} | Method: {}",
                username, request.getRequestURI(), request.getMethod());

            // For API calls, return JSON
            if (isApiCall(request)) {
                response.setContentType("application/json");
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"You do not have permission to access this resource\",\"status\":403}");
                return;
            }

            // For page requests, set status and forward to the not-authorized view
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            request.setAttribute("message", "You do not have the necessary permissions to access this page.");
            request.getRequestDispatcher("/not-authorized").forward(request, response);
        }

        private boolean isApiCall(HttpServletRequest request) {
            String accept = request.getHeader("Accept");
            String contentType = request.getHeader("Content-Type");
            String requestUri = request.getRequestURI();

            return (accept != null && accept.contains("application/json")) ||
                   (contentType != null && contentType.contains("application/json")) ||
                   (requestUri != null && requestUri.contains("/api/")) ||
                   "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
        }
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
     */
    private static class OAuth2ErrorHandlingFailureHandler implements AuthenticationFailureHandler {
        private static final Logger handlerLogger = LoggerFactory.getLogger(OAuth2ErrorHandlingFailureHandler.class);

        @Override
        public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                org.springframework.security.core.AuthenticationException exception)
                throws IOException, ServletException {
            handlerLogger.error("[OAUTH2-FLOW] OAuth2 login failure for request from " + request.getRemoteAddr() + ": " + exception.getMessage());
            handlerLogger.debug("[OAUTH2-FLOW] OAuth2 login failure details", exception);

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

            if (errorDetails != null) {
                request.getSession().setAttribute("oauth2_error_details", errorDetails);
                handlerLogger.info("[OAUTH2-FLOW] Stored error details in session: {}", errorDetails);
            }

            String redirectUrl = "/login?error=" + errorType;
            handlerLogger.info("[OAUTH2-FLOW] Redirecting to: {}", redirectUrl);
            response.sendRedirect(request.getContextPath() + redirectUrl);
        }
    }

    /**
     * Creates UserDetailsService with users loaded from properties for form-based authentication
     */
    @Bean
    public UserDetailsService userDetailsService() {
        List<org.springframework.security.core.userdetails.UserDetails> users = new ArrayList<>();

        if (userProperties != null && !userProperties.isEmpty()) {
            for (Map.Entry<String, String> entry : userProperties.entrySet()) {
                String username = entry.getKey();
                String value = entry.getValue();
                String[] parts = value.split(",", 2);
                String password = parts[0].trim();

                var userBuilder = User.withUsername(username)
                    .password(passwordEncoder().encode(password));

                if (parts.length > 1) {
                    String[] roles = parts[1].split(",");
                    for (String role : roles) {
                        userBuilder.roles(role.trim());
                    }
                } else {
                    userBuilder.roles("ADMIN");
                }

                users.add(userBuilder.build());
                logger.debug("Loaded local user: " + username);
            }
        }

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
