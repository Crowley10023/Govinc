package com.govinc.configuration;

import com.govinc.user.Role;
import com.govinc.authorization.AuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Filter that augments the Authorities on the Authentication based on the application's User role in DB.
 * Uses AuthorizationService.getRoleFromAuthentication(auth) to reliably resolve the app role for OIDC and form users,
 * then ensures corresponding ROLE_* GrantedAuthorities are present so security matchers like hasRole('ADMIN') work.
 */
@Component
public class GrantedAuthoritiesAugmentationFilter extends OncePerRequestFilter {

    @Autowired
    private AuthorizationService authorizationService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
                // Try to resolve role using AuthorizationService (handles OIDC principal resolution)
                Role role = null;
                try {
                    if (authorizationService != null) {
                        role = authorizationService.getRoleFromAuthentication(auth);
                    }
                } catch (Throwable ignored) {
                }

                if (role != null) {
                    Collection<? extends GrantedAuthority> existing = auth.getAuthorities();
                    List<GrantedAuthority> merged = new ArrayList<>();
                    if (existing != null) merged.addAll(existing);

                    // Ensure ADMIN role authority is present when user role is ADMIN
                    if (role == Role.ADMIN) {
                        SimpleGrantedAuthority adminAuth = new SimpleGrantedAuthority("ROLE_ADMIN");
                        if (!merged.contains(adminAuth)) merged.add(adminAuth);
                    }

                    // Map other roles to ROLE_* authorities
                    if (role != Role.ADMIN) {
                        SimpleGrantedAuthority mapped = new SimpleGrantedAuthority("ROLE_" + role.name());
                        if (!merged.contains(mapped)) merged.add(mapped);
                    }

                    // If we've added or changed authorities, replace Authentication
                    if (merged.size() != (existing != null ? existing.size() : 0)) {
                        UsernamePasswordAuthenticationToken newAuth = new UsernamePasswordAuthenticationToken(
                                auth.getPrincipal(), auth.getCredentials(), merged);
                        newAuth.setDetails(auth.getDetails());
                        SecurityContextHolder.getContext().setAuthentication(newAuth);
                    }
                }
            }
        } catch (Throwable t) {
            // Avoid failing authentication path because of augmentation problems
        }

        filterChain.doFilter(request, response);
    }
}
