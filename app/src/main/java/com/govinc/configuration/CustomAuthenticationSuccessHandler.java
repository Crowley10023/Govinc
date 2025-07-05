package com.govinc.configuration;

import com.govinc.user.User;
import com.govinc.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.Properties;
import java.io.FileInputStream;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    @Autowired
    private UserRepository userRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        String username = null;
        String email = null;

        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            username = oidcUser.getPreferredUsername();
            if (username == null) username = oidcUser.getEmail();
            email = oidcUser.getEmail();
        } else if (authentication.getPrincipal() instanceof UserDetails userDetails) {
            username = userDetails.getUsername();
            // Check users.properties for email
            String propFile = "app/src/main/resources/config/users.properties";
            try (FileInputStream in = new FileInputStream(propFile)) {
                Properties props = new Properties();
                props.load(in);
                String entry = props.getProperty(username);
                if (entry != null && entry.contains(",")) {
                    email = entry.split(",", 2)[1].trim();
                } else {
                    email = username + "@local";
                }
            } catch (Exception e) {
                email = username + "@local";
            }
        } else if (authentication.getPrincipal() instanceof String str) {
            username = str;
            email = username + "@local";
        }

        // Only insert if not present
        if (username != null) {
            final String finalUsername = username;
            Optional<User> existing = userRepository.findAll()
                .stream().filter(u -> finalUsername.equals(u.getName())).findFirst();
            if (existing.isEmpty()) {
                User user = new User(finalUsername, email);
                userRepository.save(user);
            }
        }

        // Continue with default behavior
        response.sendRedirect("/");
    }
}
