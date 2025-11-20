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
    
    /**
     * Add authorization flags to all views for conditional navigation display
     */
    @ModelAttribute("canAccessConfig")
    public boolean canAccessConfig() {
        return authorizationService.canAccessConfig();
    }
    
    @ModelAttribute("canAccessSecurityFramework")
    public boolean canAccessSecurityFramework() {
        return authorizationService.canAccessSecurityFramework();
    }
    
    @ModelAttribute("canAccessOrganization")
    public boolean canAccessOrganization() {
        return authorizationService.canAccessOrganization();
    }
    
    @ModelAttribute("canCreateAssessment")
    public boolean canCreateAssessment() {
        return authorizationService.canCreateAssessment();
    }
    
    @ModelAttribute("canViewAssessmentList")
    public boolean canViewAssessmentList() {
        return authorizationService.canViewAssessmentList();
    }
    
    @ModelAttribute("canAccessCompliance")
    public boolean canAccessCompliance() {
        return authorizationService.canAccessCompliance();
    }
    
    @ModelAttribute("canAccessStatistics")
    public boolean canAccessStatistics() {
        return authorizationService.canAccessStatistics();
    }
    
    @ModelAttribute("canAccessAssessmentUrls")
    public boolean canAccessAssessmentUrls() {
        return authorizationService.canAccessAssessmentUrls();
    }
}
