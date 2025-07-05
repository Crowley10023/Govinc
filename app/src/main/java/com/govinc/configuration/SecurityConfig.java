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

    @Autowired
    private AuthenticationSuccessHandler customAuthenticationSuccessHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
        String adminProps = "app/src/main/resources/config/users.properties";
        try (java.io.InputStream in = new java.io.FileInputStream(adminProps)) {
            java.util.Properties p = new java.util.Properties();
            p.load(in);
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
            }
        } catch (Exception e) {
            manager.createUser(User.withUsername("admin")
                    .password(passwordEncoder().encode("admin"))
                    .roles("ADMIN").build());
        }
        return manager;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain oauth2SecurityFilterChain(HttpSecurity http) throws Exception {
        String provider = iamConfig.getProvider();
        if (provider.equalsIgnoreCase("KEYCLOAK") || provider.equalsIgnoreCase("AZURE")) {
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
            return http.build();
        }
        // Return null if not an oauth2 provider, no bean will be registered
        return null;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain formLoginSecurityFilterChain(HttpSecurity http) throws Exception {
        String provider = iamConfig.getProvider();
        if (!provider.equalsIgnoreCase("KEYCLOAK") && !provider.equalsIgnoreCase("AZURE")) {
            // For MOCK or not set, use form login
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
            return http.build();
        }
        // Return null if not a form login provider, no bean will be registered
        return null;
    }
}
