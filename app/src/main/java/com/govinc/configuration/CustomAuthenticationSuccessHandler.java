package com.govinc.configuration;

import com.govinc.user.User;
import com.govinc.user.UserRepository;
import com.govinc.user.Role;
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

        String email = null;
        String firstName = null;
        String lastName = null;

        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            logger.info("[OAUTH2-FLOW] OidcUser detected");
            email = oidcUser.getEmail();
            // Extract first/last name from standard OIDC claims (given_name / family_name)
            firstName = oidcUser.getGivenName();
            lastName = oidcUser.getFamilyName();
            // Fall back: split preferred_username when name claims are absent
            if (firstName == null) {
                String pref = oidcUser.getPreferredUsername();
                if (pref != null && pref.contains(".")) {
                    String[] parts = pref.split("\\.", 2);
                    firstName = parts[0];
                    if (lastName == null) lastName = parts[1];
                } else {
                    firstName = pref != null ? pref : email;
                }
            }
            if (lastName == null) lastName = "";
            logger.info("[OAUTH2-FLOW] OidcUser claims: email={}, givenName={}, familyName={}", email, firstName, lastName);
        } else if (authentication.getPrincipal() instanceof UserDetails userDetails) {
            logger.info("[OAUTH2-FLOW] UserDetails detected (form login)");
            String username = userDetails.getUsername();
            String propKey = "users." + username;
            String entry = env.getProperty(propKey);
            if (entry != null && entry.contains(",")) {
                email = entry.split(",", 2)[1].trim();
            } else {
                email = username + "@local";
            }
            firstName = username;
            lastName = "";
        } else if (authentication.getPrincipal() instanceof String str) {
            logger.info("[OAUTH2-FLOW] String principal detected");
            email = str + "@local";
            firstName = str;
            lastName = "";
        }

        logger.info("[OAUTH2-FLOW] Resolved email: {} | firstName: {} | lastName: {}", email, firstName, lastName);
        if (email != null) {
            Optional<com.govinc.user.User> existingByEmail = userRepository.findByEmail(email);
            if (existingByEmail.isPresent()) {
                // User exists — update firstName/lastName from the identity provider
                User found = existingByEmail.get();
                if (firstName != null) found.setFirstName(firstName);
                if (lastName != null) found.setLastName(lastName);
                userRepository.save(found);
                logger.info("[OAUTH2-FLOW] Updated existing user names: {} / {}", found.getName(), found.getEmail());
            } else {
                // New user — create with role ASSESSOR (admins must be promoted manually)
                // Exception: local form-login admin always gets ADMIN role
                String fn = firstName != null ? firstName : "";
                String ln = lastName != null ? lastName : "";
                User user = new User(fn, ln, email);
                if (authentication.getPrincipal() instanceof UserDetails ud
                        && "admin".equalsIgnoreCase(ud.getUsername())) {
                    user.setRole(Role.ADMIN);
                } else {
                    user.setRole(Role.ASSESSOR);
                }
                userRepository.save(user);
                logger.info("[OAUTH2-FLOW] Created new user: {} / {}", user.getName(), user.getEmail());
            }
        } else {
            logger.error("[OAUTH2-FLOW] Failed to resolve email from authentication");
        }
        
        logger.info("[OAUTH2-FLOW] Redirecting to /");
        // Continue with default behavior
        response.sendRedirect("/");
    }
}
