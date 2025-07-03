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
        // Always provide an admin fallback user for MOCK or unconfigured
        if (provider.equalsIgnoreCase("MOCK") || provider.isEmpty()) {
            InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
            manager.createUser(User.withUsername("admin")
                  .password(passwordEncoder().encode("admin"))
                  .roles("ADMIN").build());
            return manager;
        }
        // If a real IDP, no local users provided
        return new InMemoryUserDetailsManager();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain oauth2SecurityFilterChain(HttpSecurity http) throws Exception {
        String provider = (iamConfig.getProvider() == null) ? "MOCK" : iamConfig.getProvider();
        if (provider.equalsIgnoreCase("KEYCLOAK")) {
            http
                .authorizeHttpRequests(authz -> authz
                    .requestMatchers("/configuration/**", "/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                    .anyRequest().authenticated()
                )
                .oauth2Login(Customizer.withDefaults());
            // Use application.properties (via Spring Boot OIDC) for Keycloak

        } else if (provider.equalsIgnoreCase("AZURE")) {
            http
                .authorizeHttpRequests(authz -> authz
                    .requestMatchers("/configuration/**", "/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                    .anyRequest().authenticated()
                )
                .oauth2Login(Customizer.withDefaults());
            // Use application.properties for Azure OIDC

        } else { // MOCK or not set
            http
                .authorizeHttpRequests(authz -> authz
                    .requestMatchers("/login", "/configuration/**", "/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                    .anyRequest().authenticated()
                )
                .formLogin(form -> form
                    .defaultSuccessUrl("/", true)
                    .permitAll()
                )
                .logout(logout -> logout.permitAll());
        }
        return http.build();
    }
}
