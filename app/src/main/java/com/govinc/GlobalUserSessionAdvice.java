package com.govinc;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalUserSessionAdvice {
    @ModelAttribute("userName")
    public String addUserNameToModel() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() &&
            !(authentication.getPrincipal() instanceof String principal && principal.equals("anonymousUser"))) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof OidcUser oidcUser) {
                if (oidcUser.getFullName() != null) return oidcUser.getFullName();
                if (oidcUser.getPreferredUsername() != null) return oidcUser.getPreferredUsername();
                if (oidcUser.getEmail() != null) return oidcUser.getEmail();
            } else if (principal instanceof UserDetails userDetails) {
                return userDetails.getUsername();
            } else if (principal instanceof String str) {
                return str;
            }
        }
        return null;
    }
    @ModelAttribute("userId")
    public String addUserIdToModel() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() &&
            !(authentication.getPrincipal() instanceof String principal && principal.equals("anonymousUser"))) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof OidcUser oidcUser) {
                if (oidcUser.getSubject() != null) return oidcUser.getSubject();
                if (oidcUser.getPreferredUsername() != null) return oidcUser.getPreferredUsername();
                if (oidcUser.getEmail() != null) return oidcUser.getEmail();
            } else if (principal instanceof UserDetails userDetails) {
                return userDetails.getUsername();
            } else if (principal instanceof String str) {
                return str;
            }
        }
        return null;
    }
}

