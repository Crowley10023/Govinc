package com.govinc.configuration;

import com.govinc.service.AuthConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

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
            "/admin/auth-config"
    };

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Autowired
    private CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;
    
    @Autowired(required = false)
    private AuthConfigService authConfigService;

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
        
        // Only configure OAuth2 login if OAuth2 providers are available and configured
        try {
            if (authConfigService != null && authConfigService.hasOAuth2Providers()) {
                http.oauth2Login(oauth2 -> oauth2
                    .loginPage("/login") // Redirects to our login page for OAuth
                    .successHandler(customAuthenticationSuccessHandler)
                    .failureHandler(oauth2AuthenticationFailureHandler()));
                logger.info("OAuth2 login configured with available providers");
            } else {
                logger.info("No OAuth2 providers available - using form authentication only");
            }
        } catch (Exception e) {
            logger.warn("Failed to configure OAuth2 login - falling back to form authentication only: {}", e.getMessage());
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
                logger.error("OAuth2 login failure", exception);
                super.onAuthenticationFailure(request, response, exception);
            }
        };
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager(
            User.withUsername("admin")
                .password(passwordEncoder().encode("admin"))
                .roles("ADMIN")
                .build()
        );
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}