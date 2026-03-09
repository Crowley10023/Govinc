package com.govinc.authorization;

import com.govinc.assessment.Assessment;
import com.govinc.assessment.AssessmentRepository;
import com.govinc.organization.OrgUnit;
import com.govinc.organization.OrgUnitService;
import com.govinc.user.Role;
import com.govinc.user.User;
import com.govinc.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Centralized authorization service for role-based access control (RBAC).
 * All authorization checks should go through this service for consistent policy enforcement.
 * 
 * Authorization Rules:
 * - ADMIN: Full access to everything
 * - INFORMATION_SECURITY_MANAGER: Full access except configuration (no config tab access)
 * - ORGANISATION_TEAM_LEADER: Access only to assessments in their organization(s) or children, plus their org units
 * - ASSESSMENT_DELEGATE: Only access to assessments where they are assigned users
 * 
 * Exception: assessment-direct endpoints are publicly accessible without authentication
 */
@Service
public class AuthorizationService {
    
    private static final Logger logger = Logger.getLogger(AuthorizationService.class.getName());
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private AssessmentRepository assessmentRepository;
    
    @Autowired
    private OrgUnitService orgUnitService;
    
    /**
     * Get the currently authenticated user from Spring Security context.
     * Returns null if no user is authenticated or user not found in database.
     * 
     * For OAuth2/OIDC providers (Keycloak, Azure), resolves the username from the security principal.
     * For form-based authentication, uses the standard username.
     */
    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        
        String username = null;
        
        // Handle OIDC/OAuth2 users (Keycloak, Azure, etc.)
        if (auth.getPrincipal() instanceof org.springframework.security.oauth2.core.oidc.user.OidcUser oidcUser) {
            // Try preferred_username first (Keycloak), then email, then sub (Azure)
            username = oidcUser.getPreferredUsername();
            if (username == null) {
                username = oidcUser.getEmail();
            }
            if (username == null) {
                String sub = (String) oidcUser.getClaims().get("sub");
                if (sub != null) {
                    username = sub;
                }
            }
            logger.info("OAuth2 user resolved: " + username);
        } else {
            // Handle form-based authentication
            username = auth.getName();
        }
        
        if (username == null) {
            logger.warning("Could not resolve username from authentication principal");
            return null;
        }
        
        Optional<User> userOpt = userRepository.findByName(username);
        if (userOpt.isEmpty()) {
            logger.warning("User " + username + " authenticated but not found in database");
        }
        return userOpt.orElse(null);
    }
    
    /**
     * Get current user's role, or null if not authenticated.
     * 
     * This method is called regardless of authentication provider (Keycloak, Azure, form-based).
     * User roles are always fetched from the app database, NOT from the identity provider.
     */
    public Role getCurrentUserRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return getRoleFromAuthentication(auth);
    }

    /**
     * Resolve Role from a provided Authentication object (does not rely on SecurityContextHolder).
     */
    public Role getRoleFromAuthentication(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }

        String username = null;
        if (auth.getPrincipal() instanceof org.springframework.security.oauth2.core.oidc.user.OidcUser oidcUser) {
            username = oidcUser.getPreferredUsername();
            if (username == null) username = oidcUser.getEmail();
            if (username == null) {
                Object sub = oidcUser.getClaims().get("sub");
                if (sub != null) username = sub.toString();
            }
        } else if (auth.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails ud) {
            username = ud.getUsername();
        } else if (auth.getPrincipal() instanceof String str) {
            username = str;
        }

        if (username == null) return null;

        java.util.Optional<User> userOpt = userRepository.findByName(username);
        if (userOpt.isEmpty()) {
            logger.fine("Authenticated principal '" + username + "' not found in DB when resolving role");
            return null;
        }
        User user = userOpt.get();
        if (user.getName() != null && user.getName().equalsIgnoreCase("admin")) {
            return Role.ADMIN;
        }
        return user.getRole();
    }
    
    /**
     * Check if user has admin role
     */
    public boolean isAdmin() {
        Role role = getCurrentUserRole();
        return role == Role.ADMIN;
    }
    
    /**
     * Check if user has information security manager role
     * Note: ADMIN should be considered to have all permissions, so treat ADMIN as an ISM for checks
     */
    public boolean isInformationSecurityManager() {
        Role role = getCurrentUserRole();
        return role == Role.ADMIN || role == Role.INFORMATION_SECURITY_MANAGER;
    }

    /**
     * Check if user is organization team leader
     * Admins should be treated as having all roles for authorization checks
     */
    public boolean isOrganisationTeamLeader() {
        Role role = getCurrentUserRole();
        return role == Role.ADMIN || role == Role.ORGANISATION_TEAM_LEADER;
    }

    /**
     * Check if user is assessment delegate
     * Admins should be treated as having all roles for authorization checks
     */
    public boolean isAssessmentDelegate() {
        Role role = getCurrentUserRole();
        return role == Role.ADMIN || role == Role.ASSESSMENT_DELEGATE;
    }
    
    /**
     * Check if user can access configuration pages.
     * Only ADMIN can access configuration.
     */
    public boolean canAccessConfig() {
        Role role = getCurrentUserRole();
        return role == Role.ADMIN;
    }
    
    /**
     * Check if user can access security framework (catalogs, controls, domains, maturity models).
     * ADMIN and INFORMATION_SECURITY_MANAGER can access.
     */
    public boolean canAccessSecurityFramework() {
        Role role = getCurrentUserRole();
        return role == Role.ADMIN || role == Role.INFORMATION_SECURITY_MANAGER;
    }
    
    /**
     * Check if user can access organization management (users, org units, org services).
     * ADMIN and INFORMATION_SECURITY_MANAGER can access.
     */
    public boolean canAccessOrganization() {
        Role role = getCurrentUserRole();
        return role == Role.ADMIN || role == Role.INFORMATION_SECURITY_MANAGER;
    }
    
    /**
     * Check if user can create assessments.
     * ADMIN and INFORMATION_SECURITY_MANAGER can create.
     */
    public boolean canCreateAssessment() {
        Role role = getCurrentUserRole();
        return role == Role.ADMIN || role == Role.INFORMATION_SECURITY_MANAGER;
    }
    
    /**
     * Check if user can view list of all assessments.
     * ADMIN and INFORMATION_SECURITY_MANAGER can view all.
     * ORGANISATION_TEAM_LEADER can view assessments in their org and children.
     * ASSESSMENT_DELEGATE can view only their assigned assessments (but this is filtered elsewhere).
     */
    public boolean canViewAssessmentList() {
        Role role = getCurrentUserRole();
        return role == Role.ADMIN || role == Role.INFORMATION_SECURITY_MANAGER 
            || role == Role.ORGANISATION_TEAM_LEADER || role == Role.ASSESSMENT_DELEGATE;
    }
    
    /**
     * Check if user can access a specific assessment.
     * 
     * @param assessmentId the assessment ID
     * @return true if user has permission to view this assessment
     */
    public boolean canAccessAssessment(Long assessmentId) {
        User user = getCurrentUser();
        if (user == null) {
            logger.warning("Cannot access assessment - no user authenticated");
            return false;
        }
        
        Role role = user.getRole();
        
        // Admins and ISMs can access all assessments
        if (role == Role.ADMIN || role == Role.INFORMATION_SECURITY_MANAGER) {
            return true;
        }
        
        Optional<Assessment> assessmentOpt = assessmentRepository.findById(assessmentId);
        if (assessmentOpt.isEmpty()) {
            return false;
        }
        Assessment assessment = assessmentOpt.get();
        
        // Organisation Team Leaders: check if assessment is in any of their org units or children
        if (role == Role.ORGANISATION_TEAM_LEADER) {
            Set<Long> accessibleOrgUnitIds = getAccessibleOrgUnitIdsForUser(user);
            if (accessibleOrgUnitIds.isEmpty()) {
                logger.warning("Organisation Team Leader user " + user.getId() + " (" + user.getName() + ") is not leading any organisation units. Cannot access assessments.");
                return false;
            }
            
            OrgUnit assessmentOrg = assessment.getOrgUnit();
            if (assessmentOrg == null) {
                logger.warning("Assessment " + assessmentId + " has no organisation unit assigned. Team leader " + user.getName() + " cannot access it.");
                return false;
            }

            if (assessmentOrg.getId() != null && accessibleOrgUnitIds.contains(assessmentOrg.getId())) {
                logger.fine("Team leader " + user.getName() + " can access assessment " + assessmentId + " (org: " + assessmentOrg.getName() + ")");
                return true;
            }
            
            logger.fine("Team leader " + user.getName() + " cannot access assessment " + assessmentId + " (not in any of their org units)");
            return false;
        }
        
        // Assessment Delegates: check if they are assigned to this assessment
        if (role == Role.ASSESSMENT_DELEGATE) {
            Set<User> assignedUsers = assessment.getUsers();
            if (assignedUsers == null) {
                return false;
            }
            return assignedUsers.contains(user);
        }
        
        return false;
    }
    
    /**
     * Check if user can modify a specific assessment.
     * Same permissions as viewing, but may be stricter for delegates.
     * 
     * @param assessmentId the assessment ID
     * @return true if user has permission to modify this assessment
     */
    public boolean canModifyAssessment(Long assessmentId) {
        User user = getCurrentUser();
        if (user == null) {
            return false;
        }
        
        Role role = user.getRole();
        
        // Admins and ISMs can modify all assessments
        if (role == Role.ADMIN || role == Role.INFORMATION_SECURITY_MANAGER) {
            return true;
        }
        
        // Organisation Team Leaders and Assessment Delegates can modify if they can access
        return canAccessAssessment(assessmentId);
    }
    
    /**
     * Check if user can delete a specific assessment.
     * Only ADMIN and INFORMATION_SECURITY_MANAGER can delete.
     * 
     * @param assessmentId the assessment ID
     * @return true if user has permission to delete this assessment
     */
    public boolean canDeleteAssessment(Long assessmentId) {
        Role role = getCurrentUserRole();
        return role == Role.ADMIN || role == Role.INFORMATION_SECURITY_MANAGER;
    }
    
    /**
     * Check if user can view organization units.
     */
    public boolean canViewOrgUnits() {
        Role role = getCurrentUserRole();
        return role == Role.ADMIN || role == Role.INFORMATION_SECURITY_MANAGER;
    }
    
    /**
     * Check if user can access their own organization units and children.
     * For Organization Team Leaders, returns all org units they lead and their children.
     * For others, returns only if they are ADMIN or ISM.
     * 
     * @return Set of org units accessible to user, or empty set
     */
    public Set<OrgUnit> getAccessibleOrgUnits() {
        User user = getCurrentUser();
        if (user == null) {
            return new HashSet<>();
        }
        
        Role role = user.getRole();
        Set<OrgUnit> accessible = new HashSet<>();
        
        if (role == Role.ADMIN || role == Role.INFORMATION_SECURITY_MANAGER) {
            // Can access all org units (caller may need to fetch separately)
            return null; // Signal that all are accessible
        }
        
        if (role == Role.ORGANISATION_TEAM_LEADER) {
            Set<OrgUnit> userLeadingOrgs = user.getLeadsOrgUnits();
            if (userLeadingOrgs != null && !userLeadingOrgs.isEmpty()) {
                for (OrgUnit userOrg : userLeadingOrgs) {
                    if (userOrg == null || userOrg.getId() == null) {
                        continue;
                    }
                    // Resolve from repository recursively to avoid partial hierarchy loading.
                    OrgUnit root = orgUnitService.getOrgUnitWithChildrenRecursive(userOrg.getId()).orElse(userOrg);
                    accessible.add(root);
                    addChildrenToSet(root, accessible);
                }
            }
        }
        
        return accessible;
    }
    
    /**
     * Check if an org unit is in the tree (itself or any descendant of treeRoot).
     * For organization team leaders: they can access assessments in their own org unit
     * and all child org units (descendant hierarchy).
     * 
     * @param checkOrg the org unit to check (assessment's org unit)
     * @param treeRoot the root org unit to search in (user's org unit)
     * @return true if checkOrg is the treeRoot or a descendant of treeRoot
     */
    private boolean isOrgUnitInTree(OrgUnit checkOrg, OrgUnit treeRoot) {
        if (checkOrg == null || treeRoot == null) {
            return false;
        }
        
        // Same org unit
        if (checkOrg.getId().equals(treeRoot.getId())) {
            return true;
        }
        
        // Check if checkOrg is a descendant (child, grandchild, etc.) of treeRoot
        return isDescendantOf(checkOrg, treeRoot);
    }
    
    /**
     * Check if an org unit is a descendant of another org unit.
     * 
     * @param potential the potential child org unit
     * @param parent the potential parent org unit
     * @return true if potential is a descendant of parent
     */
    private boolean isDescendantOf(OrgUnit potential, OrgUnit parent) {
        if (potential == null || parent == null) {
            return false;
        }
        OrgUnit current = potential;
        while (current != null && current.getParent() != null) {
            current = current.getParent();
            if (current.getId().equals(parent.getId())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Recursively add all children of an org unit to a set.
     */
    private void addChildrenToSet(OrgUnit orgUnit, Set<OrgUnit> set) {
        if (orgUnit.getChildren() != null) {
            for (OrgUnit child : orgUnit.getChildren()) {
                if (!set.contains(child)) {
                    set.add(child);
                    addChildrenToSet(child, set);
                }
            }
        }
    }

    /**
     * Returns all org-unit IDs a team leader can access: led units and all descendants.
     */
    private Set<Long> getAccessibleOrgUnitIdsForUser(User user) {
        Set<Long> ids = new HashSet<>();
        if (user == null) {
            return ids;
        }

        Set<OrgUnit> userLeadingOrgs = user.getLeadsOrgUnits();
        if (userLeadingOrgs == null || userLeadingOrgs.isEmpty()) {
            return ids;
        }

        for (OrgUnit userOrg : userLeadingOrgs) {
            if (userOrg == null || userOrg.getId() == null) {
                continue;
            }
            OrgUnit root = orgUnitService.getOrgUnitWithChildrenRecursive(userOrg.getId()).orElse(userOrg);
            addOrgUnitIdsRecursively(root, ids);
        }

        return ids;
    }

    private void addOrgUnitIdsRecursively(OrgUnit orgUnit, Set<Long> ids) {
        if (orgUnit == null || orgUnit.getId() == null || !ids.add(orgUnit.getId())) {
            return;
        }
        if (orgUnit.getChildren() == null) {
            return;
        }
        for (OrgUnit child : orgUnit.getChildren()) {
            addOrgUnitIdsRecursively(child, ids);
        }
    }
    
    /**
     * Check if user can access compliance check/view.
     * ADMIN and INFORMATION_SECURITY_MANAGER can access.
     */
    public boolean canAccessCompliance() {
        Role role = getCurrentUserRole();
        return role == Role.ADMIN || role == Role.INFORMATION_SECURITY_MANAGER;
    }
    
    /**
     * Check if user can access statistics.
     * ADMIN and INFORMATION_SECURITY_MANAGER can access.
     */
    public boolean canAccessStatistics() {
        Role role = getCurrentUserRole();
        return role == Role.ADMIN || role == Role.INFORMATION_SECURITY_MANAGER;
    }
    
    /**
     * Check if user can access assessment URLs management.
     * ADMIN and INFORMATION_SECURITY_MANAGER can access.
     */
    public boolean canAccessAssessmentUrls() {
        Role role = getCurrentUserRole();
        return role == Role.ADMIN || role == Role.INFORMATION_SECURITY_MANAGER;
    }
}
