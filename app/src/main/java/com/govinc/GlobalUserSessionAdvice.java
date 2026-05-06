package com.govinc;

import com.govinc.authorization.AuthorizationService;
import com.govinc.service.GeneralConfigService;
import com.govinc.user.UserRepository;
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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GeneralConfigService generalConfigService;
    
    @ModelAttribute("userName")
    public String addUserNameToModel() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() &&
                !(authentication.getPrincipal() instanceof String principal && principal.equals("anonymousUser"))) {
                Object principal = authentication.getPrincipal();
                String email = null;
                if (principal instanceof OidcUser oidcUser) {
                    email = oidcUser.getEmail();
                } else if (principal instanceof UserDetails userDetails) {
                    email = userDetails.getUsername() + "@local";
                } else if (principal instanceof String str) {
                    email = str + "@local";
                }
                if (email != null) {
                    var user = userRepository.findByEmail(email);
                    if (user.isPresent()) return user.get().getName();
                }
                // Fallback: no DB record yet (e.g. first login before handler completes)
                if (principal instanceof OidcUser oidcUser) {
                    if (oidcUser.getEmail() != null) return oidcUser.getEmail();
                } else if (principal instanceof UserDetails userDetails) {
                    return userDetails.getUsername();
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

    @ModelAttribute("canAccessGovernance")
    public boolean canAccessGovernance() {
        try {
            return authorizationService != null && authorizationService.canAccessGovernance();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Add current user's role (display name) to the model for templates.
     */
    @ModelAttribute("userRole")
    public String addUserRoleToModel() {
        try {
            if (authorizationService == null) return null;
            com.govinc.user.Role role = authorizationService.getCurrentUserRole();
            if (role == null) return null;
            return role.getDisplayName();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Expose configured session timeout so templates can embed it for the frontend timer.
     */
    @ModelAttribute("sessionTimeoutMinutes")
    public int addSessionTimeoutToModel() {
        try {
            return generalConfigService != null ? generalConfigService.getSessionTimeoutMinutes() : 30;
        } catch (Throwable t) {
            return 30;
        }
    }
    }