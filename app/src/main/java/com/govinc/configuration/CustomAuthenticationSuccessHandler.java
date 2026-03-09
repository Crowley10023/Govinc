package com.govinc.configuration;

import com.govinc.user.User;
import com.govinc.user.UserRepository;
import com.govinc.session.UserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
    private static final Logger logger = LoggerFactory.getLogger(CustomAuthenticationSuccessHandler.class);
    
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private org.springframework.core.env.Environment env;
    @Autowired
    private UserSession userSession;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        String sessionId = request.getSession().getId();
        logger.info("[OAUTH2-FLOW] onAuthenticationSuccess triggered | SessionID: {}", sessionId);
        logger.info("[OAUTH2-FLOW] Authentication object: {} | Name: {}", 
            authentication.getClass().getSimpleName(), authentication.getName());
        logger.info("[OAUTH2-FLOW] Principal type: {}", authentication.getPrincipal().getClass().getSimpleName());
        
        // Log request details
        logger.info("[OAUTH2-FLOW] Request URL: {} | Method: {}", request.getRequestURL(), request.getMethod());
        logger.info("[OAUTH2-FLOW] Request from: {} | Protocol: {}", request.getRemoteAddr(), request.getProtocol());
        
        // Log cookies in response
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("JSESSIONID".equals(cookie.getName())) {
                    logger.info("[OAUTH2-FLOW] Incoming JSESSIONID: {}", cookie.getValue());
                }
            }
        } else {
            logger.warn("[OAUTH2-FLOW] No cookies in success callback request");
        }

        String username = null;
        String email = null;

        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            logger.info("[OAUTH2-FLOW] OidcUser detected");
            // Try preferred_username first (Keycloak), then fall back to email, then to sub (Azure)
            username = oidcUser.getPreferredUsername();
            logger.debug("[OAUTH2-FLOW] preferred_username: {}", username);
            if (username == null) {
                username = oidcUser.getEmail();
                logger.debug("[OAUTH2-FLOW] Fallback to email: {}", username);
            }
            if (username == null) {
                // For Azure, use 'sub' claim as username identifier
                String sub = (String) oidcUser.getClaims().get("sub");
                logger.debug("[OAUTH2-FLOW] Fallback to sub claim: {}", sub);
                if (sub != null) {
                    username = sub;
                }
            }
            email = oidcUser.getEmail();
            logger.info("[OAUTH2-FLOW] OidcUser loaded with attributes | Claims count: {}", oidcUser.getClaims().size());
        } else if (authentication.getPrincipal() instanceof UserDetails userDetails) {
            logger.info("[OAUTH2-FLOW] UserDetails detected (form login)");
            username = userDetails.getUsername();
            // Check users.* property for email in application.properties
            String propKey = "users." + username;
            String entry = env.getProperty(propKey);
            if (entry != null && entry.contains(",")) {
                email = entry.split(",", 2)[1].trim();
            } else {
                email = username + "@local";
            }
        } else if (authentication.getPrincipal() instanceof String str) {
            logger.info("[OAUTH2-FLOW] String principal detected");
            username = str;
            email = username + "@local";
        }

        // Only insert if not present. If a user with the same email exists, do NOT create a new user.
        logger.info("[OAUTH2-FLOW] Resolved username: {} | email: {}", username, email);
        if (username != null) {
            Optional<com.govinc.user.User> existingByName = userRepository.findByName(username);
            logger.info("[OAUTH2-FLOW] User exists in db by name? {}", existingByName.isPresent());
            if (existingByName.isEmpty()) {
                Optional<com.govinc.user.User> existingByEmail = Optional.empty();
                if (email != null) {
                    existingByEmail = userRepository.findByEmail(email);
                }

                if (existingByEmail.isPresent()) {
                    // Do not create a new user if an account with the same email already exists.
                    User found = existingByEmail.get();
                    logger.info("[OAUTH2-FLOW] User with same email already exists in DB (will not create new user): {} / {}", found.getName(), found.getEmail());
                    // Optionally, you could update the existing user's name here to match the identity provider's username
                    // if (!found.getName().equals(username)) { found.setName(username); userRepository.save(found); }
                } else {
                    logger.info("[OAUTH2-FLOW] Creating new user in DB: {} / {}", username, email);
                    User user = new User(username, email);
                    userRepository.save(user);
                }
            } else {
                logger.info("[OAUTH2-FLOW] User already exists: {}", username);
            }
        } else {
            logger.error("[OAUTH2-FLOW] Failed to resolve username from authentication");
        }
        
        logger.info("[OAUTH2-FLOW] Redirecting to /");
        // Continue with default behavior
        response.sendRedirect("/");
    }
}
