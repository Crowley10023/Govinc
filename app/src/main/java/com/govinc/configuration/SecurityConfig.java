package com.govinc.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Autowired
    private IamConfig iamConfig;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        String provider = (iamConfig.getProvider() == null) ? "MOCK" : iamConfig.getProvider();
        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
        // Always read admin from properties file
        String adminProps = "app/src/main/resources/config/users.properties";
        try (java.io.InputStream in = new java.io.FileInputStream(adminProps)) {
            java.util.Properties p = new java.util.Properties();
            p.load(in);
            // Now: allow multiple users, value = password[,email]
            for (String key : p.stringPropertyNames()) {
                String[] valParts = p.getProperty(key).split(",", 2);
                String plainOrHashed = valParts[0].trim();
                String email = valParts.length > 1 ? valParts[1].trim() : (key + "@local");
                boolean isBCrypt = plainOrHashed.startsWith("{bcrypt}");
                String pw = isBCrypt ? plainOrHashed : passwordEncoder().encode(plainOrHashed);
                manager.createUser(User.withUsername(key)
                        .password(pw)
                        .roles(key.equals("admin") ? "ADMIN" : "USER")
                        .build());
                // Optionally, store email in a static map for lookup if needed
            }
        } catch (Exception e) {
            // Fallback in-memory admin
            manager.createUser(User.withUsername("admin")
                    .password(passwordEncoder().encode("admin"))
                    .roles("ADMIN").build());
        }
        // If IDP, all users come from there, but admin always present
        return manager;
    }

    @Autowired
    private AuthenticationSuccessHandler customAuthenticationSuccessHandler;

    @Bean
    @Order(1)
    public SecurityFilterChain oauth2SecurityFilterChain(HttpSecurity http) throws Exception {
        String provider = (iamConfig.getProvider() == null) ? "MOCK" : iamConfig.getProvider();
        if (provider.equalsIgnoreCase("KEYCLOAK")) {
            http
                .authorizeHttpRequests(authz -> authz
                    .requestMatchers("/css/**", "/js/**", "/webjars/**", "/images/**").permitAll()
                    .requestMatchers("/configuration/database/restart").authenticated()
                    .requestMatchers("/configuration/**").authenticated()
                    .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                    .successHandler(customAuthenticationSuccessHandler)
                );
            // Use application.properties (via Spring Boot OIDC) for Keycloak

        } else if (provider.equalsIgnoreCase("AZURE")) {
            http
                .authorizeHttpRequests(authz -> authz
                    .requestMatchers("/css/**", "/js/**", "/webjars/**", "/images/**").permitAll()
                    .requestMatchers("/configuration/database/restart").authenticated()
                    .requestMatchers("/configuration/**").authenticated()
                    .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                    .successHandler(customAuthenticationSuccessHandler)
                );
            // Use application.properties for Azure OIDC

        } else { // MOCK or not set
            http
                .authorizeHttpRequests(authz -> authz
                    .requestMatchers("/login", "/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                    .requestMatchers("/configuration/**").authenticated()
                    .anyRequest().authenticated()
                )
                .formLogin(form -> form
                    .defaultSuccessUrl("/", true)
                    .successHandler(customAuthenticationSuccessHandler)
                    .permitAll()
                )
                .logout(logout -> logout.permitAll());
        }
        return http.build();
    }
}
