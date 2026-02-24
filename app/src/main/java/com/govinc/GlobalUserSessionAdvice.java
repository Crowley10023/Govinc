package com.govinc;

import com.govinc.authorization.AuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalUserSessionAdvice {
    
    @Autowired
    private AuthorizationService authorizationService;
    
    @ModelAttribute("userName")
    public String addUserNameToModel() {
        try {
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
        } catch (Throwable t) {
            // Safely ignore and return null so page rendering can continue
        }
        return null;
    }
    
    @ModelAttribute("userId")
    public String addUserIdToModel() {
        try {
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
        } catch (Throwable t) {
            // ignore
        }
        return null;
    }
    
    /**
     * Add authorization flags to all views for conditional navigation display
     */
    @ModelAttribute("canAccessConfig")
    public boolean canAccessConfig() {
        try {
            return authorizationService != null && authorizationService.canAccessConfig();
        } catch (Throwable t) {
            return false;
        }
    }
    
    @ModelAttribute("canAccessSecurityFramework")
    public boolean canAccessSecurityFramework() {
        try {
            return authorizationService != null && authorizationService.canAccessSecurityFramework();
        } catch (Throwable t) {
            return false;
        }
    }
    
    @ModelAttribute("canAccessOrganization")
    public boolean canAccessOrganization() {
        try {
            return authorizationService != null && authorizationService.canAccessOrganization();
        } catch (Throwable t) {
            return false;
        }
    }
    
    @ModelAttribute("canCreateAssessment")
    public boolean canCreateAssessment() {
        try {
            return authorizationService != null && authorizationService.canCreateAssessment();
        } catch (Throwable t) {
            return false;
        }
    }
    
    @ModelAttribute("canViewAssessmentList")
    public boolean canViewAssessmentList() {
        try {
            return authorizationService != null && authorizationService.canViewAssessmentList();
        } catch (Throwable t) {
            return false;
        }
    }
    
    @ModelAttribute("canAccessCompliance")
    public boolean canAccessCompliance() {
        try {
            return authorizationService != null && authorizationService.canAccessCompliance();
        } catch (Throwable t) {
            return false;
        }
    }
    
    @ModelAttribute("canAccessStatistics")
    public boolean canAccessStatistics() {
        try {
            return authorizationService != null && authorizationService.canAccessStatistics();
        } catch (Throwable t) {
            return false;
        }
    }
    
    @ModelAttribute("canAccessAssessmentUrls")
    public boolean canAccessAssessmentUrls() {
        try {
            return authorizationService != null && authorizationService.canAccessAssessmentUrls();
        } catch (Throwable t) {
            return false;
        }
    }
}