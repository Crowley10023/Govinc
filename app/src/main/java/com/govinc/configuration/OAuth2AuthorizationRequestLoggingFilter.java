package com.govinc.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Filter to log OAuth2 authorization request storage and retrieval.
 * This helps debug state parameter loss issues.
 */
public class OAuth2AuthorizationRequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(OAuth2AuthorizationRequestLoggingFilter.class);
    private static final String OAUTH2_AUTHORIZATION_REQUEST_ATTR_NAME = 
        "org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository.AUTHORIZATION_REQUEST";
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String path = request.getRequestURI();
        String sessionId = request.getSession(false) != null ? request.getSession().getId() : "NO_SESSION";
        
        if (path.contains("/oauth2") || path.contains("/login/oauth2")) {
            HttpSession session = request.getSession(false);
            
            logger.info("[OAUTH2-FLOW] [FILTER] {} {} | SessionID: {}", request.getMethod(), path, sessionId);
            
            if (session != null) {
                // Check if OAuth2AuthorizationRequest is in session before processing
                OAuth2AuthorizationRequest authReq = 
                    (OAuth2AuthorizationRequest) session.getAttribute(OAUTH2_AUTHORIZATION_REQUEST_ATTR_NAME);
                
                if (authReq != null) {
                    logger.info("[OAUTH2-FLOW] [FILTER] OAuth2AuthorizationRequest FOUND in session | State: {} | RedirectUri: {} | ClientId: {}", 
                        authReq.getState(), authReq.getRedirectUri(), authReq.getClientId());
                } else {
                    logger.warn("[OAUTH2-FLOW] [FILTER] OAuth2AuthorizationRequest NOT FOUND in session for path: {}", path);
                }
                
                // Log all session attributes for debugging
                logger.debug("[OAUTH2-FLOW] [FILTER] Session attributes count: {}", 
                    java.util.Collections.list(session.getAttributeNames()).size());
                java.util.Collections.list(session.getAttributeNames()).forEach(attr -> {
                    if (attr.contains("AUTHORIZATION") || attr.contains("oauth")) {
                        logger.debug("[OAUTH2-FLOW] [FILTER] Session attribute: {} = {}", attr, 
                            session.getAttribute(attr) != null ? session.getAttribute(attr).getClass().getSimpleName() : "null");
                    }
                });
            } else {
                logger.warn("[OAUTH2-FLOW] [FILTER] No session found for path: {}", path);
            }
        }
        
        filterChain.doFilter(request, response);
    }
}
