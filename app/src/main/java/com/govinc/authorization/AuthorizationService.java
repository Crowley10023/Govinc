package com.govinc.authorization;

import com.govinc.assessment.Assessment;
import com.govinc.assessment.AssessmentRepository;
import com.govinc.organization.OrgUnit;
import com.govinc.organization.OrgUnitService;
import com.govinc.user.Role;
import com.govinc.user.User;
import com.govinc.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
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
 * - ASSESSOR: Only access to assessments where they are assigned and only for answering/commenting
 * 
 * Exception: assessment-direct endpoints are publicly accessible without authentication
 */
@Service
public class AuthorizationService {
    
    private static final Logger logger = Logger.getLogger(AuthorizationService.class.getName());
    
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Environment environment;
    
    @Autowired
    private AssessmentRepository assessmentRepository;
    
    @Autowired
    private OrgUnitService orgUnitService;

    /** Request-scoped cache — eliminates repeated DB lookups within a single HTTP request. */
    @Autowired(required = false)
    private AuthorizationRequestCache requestCache;
    
    /**
     * Get the currently authenticated user from Spring Security context.
     * Returns null if no user is authenticated or user not found in database.
     * 
     * For OAuth2/OIDC providers (Keycloak, Azure), resolves the username from the security principal.
     * For form-based authentication, uses the standard username.
     */
    public User getCurrentUser() {
        // Check request-scoped cache first to avoid repeated DB lookups within one HTTP request
        if (requestCache != null) {
            try {
                if (requestCache.isUserResolved()) {
                    return requestCache.getCachedUser();
                }
            } catch (Exception ignored) {
                // Outside of request scope (e.g., scheduled tasks) — fall through to normal lookup
            }
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            cacheUser(null);
            return null;
        }
        
        String email = resolveEmailFromAuth(auth);
        if (email == null) {
            logger.warning("Could not resolve email from authentication principal");
            cacheUser(null);
            return null;
        }
        
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            logger.warning("User with email " + email + " authenticated but not found in database");
        }
        User result = userOpt.orElse(null);
        cacheUser(result);
        return result;
    }

    private void cacheUser(User user) {
        if (requestCache != null) {
            try { requestCache.setUser(user); } catch (Exception ignored) {}
        }
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

        // Check request-scoped cache first
        if (requestCache != null) {
            try {
                if (requestCache.isRoleResolved()) {
                    return requestCache.getCachedRole();
                }
            } catch (Exception ignored) {}
        }

        String email = resolveEmailFromAuth(auth);
        if (email == null) {
            cacheRole(null);
            return null;
        }

        java.util.Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            logger.fine("User with email '" + email + "' not found in DB when resolving role");
            cacheRole(null);
            return null;
        }
        User user = userOpt.get();
        Role role = user.getRole();
        cacheRole(role);
        return role;
    }

    /**
     * Resolves the user's email address from the authentication principal.
     * For OIDC users, reads the email claim directly.
     * For form-login users, maps the Spring Security username to the configured email address.
     */
    private String resolveEmailFromAuth(Authentication auth) {
        if (auth == null) return null;
        if (auth.getPrincipal() instanceof org.springframework.security.oauth2.core.oidc.user.OidcUser oidcUser) {
            return oidcUser.getEmail();
        } else if (auth.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails ud) {
            String username = ud.getUsername();
            String entry = environment.getProperty("users." + username);
            if (entry != null && entry.contains(",")) {
                return entry.split(",", 2)[1].trim();
            }
            return username + "@local";
        } else if (auth.getPrincipal() instanceof String str && !"anonymousUser".equals(str)) {
            return str + "@local";
        }
        return null;
    }

    private void cacheRole(Role role) {
        if (requestCache != null) {
            try { requestCache.setRole(role); } catch (Exception ignored) {}
        }
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
     * Check if user is assessor.
     */
    public boolean isAssessor() {
        Role role = getCurrentUserRole();
        return role == Role.ASSESSOR;
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
     * ASSESSMENT_DELEGATE and ASSESSOR can view only their assigned assessments (filtered elsewhere).
     */
    public boolean canViewAssessmentList() {
        Role role = getCurrentUserRole();
        return role == Role.ADMIN || role == Role.INFORMATION_SECURITY_MANAGER 
            || role == Role.ORGANISATION_TEAM_LEADER || role == Role.ASSESSMENT_DELEGATE
            || role == Role.ASSESSOR;
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

        // Leadership-based access should work independently of the user's role value.
        if (canAccessAssessmentThroughLeadership(user, assessment)) {
            return true;
        }
        
        // Assessment Delegates and Assessors: check if they are assigned to this assessment
        if (role == Role.ASSESSMENT_DELEGATE || role == Role.ASSESSOR) {
            Set<User> assignedUsers = assessment.getUsers();
            if (assignedUsers == null) {
                return false;
            }
            return assignedUsers.contains(user);
        }
        
        return false;
    }

    /**
     * Overload for callers that already hold the Assessment object, avoids an extra DB fetch.
     */
    public boolean canAccessAssessment(Assessment assessment) {
        if (assessment == null) return false;
        User user = getCurrentUser();
        if (user == null) return false;

        Role role = user.getRole();
        if (role == Role.ADMIN || role == Role.INFORMATION_SECURITY_MANAGER) return true;
        if (canAccessAssessmentThroughLeadership(user, assessment)) return true;

        if (role == Role.ASSESSMENT_DELEGATE || role == Role.ASSESSOR) {
            Set<User> assignedUsers = assessment.getUsers();
            return assignedUsers != null && assignedUsers.contains(user);
        }
        return false;
    }

    /**
     * Landing-page visibility rule: users who lead org units can see assessments
     * in those units and all descendants, regardless of their role value.
     */
    public boolean canAccessAssessmentThroughLeadership(Long assessmentId) {
        User user = getCurrentUser();
        if (user == null) {
            return false;
        }

        Optional<Assessment> assessmentOpt = assessmentRepository.findById(assessmentId);
        if (assessmentOpt.isEmpty()) {
            return false;
        }

        return canAccessAssessmentThroughLeadership(user, assessmentOpt.get());
    }

    private boolean canAccessAssessmentThroughLeadership(User user, Assessment assessment) {
        if (user == null || assessment == null) {
            return false;
        }

        Set<Long> accessibleOrgUnitIds = getAccessibleOrgUnitIdsForUser(user);
        if (accessibleOrgUnitIds.isEmpty()) {
            return false;
        }

        OrgUnit assessmentOrg = assessment.getOrgUnit();
        if (assessmentOrg == null || assessmentOrg.getId() == null) {
            return false;
        }

        return accessibleOrgUnitIds.contains(assessmentOrg.getId());
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
        if (role == Role.ORGANISATION_TEAM_LEADER || role == Role.ASSESSMENT_DELEGATE) {
            return canAccessAssessment(assessmentId);
        }

        // Assessors must not be able to modify assessment structure/settings
        return false;
    }

    /**
     * Check if user can answer questions/write comments in an assessment.
     */
    public boolean canAnswerAssessment(Long assessmentId) {
        User user = getCurrentUser();
        if (user == null) {
            return false;
        }

        Role role = user.getRole();
        if (role == Role.ADMIN || role == Role.INFORMATION_SECURITY_MANAGER
                || role == Role.ORGANISATION_TEAM_LEADER || role == Role.ASSESSMENT_DELEGATE
                || role == Role.ASSESSOR) {
            return canAccessAssessment(assessmentId);
        }

        return false;
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
