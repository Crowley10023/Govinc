package com.govinc.user;

import com.govinc.session.UserSession;
import com.govinc.authorization.AuthorizationService;
import com.govinc.authorization.UnauthorizedException;
import com.govinc.assessment.Assessment;
import com.govinc.assessment.AssessmentRepository;
import com.govinc.organization.OrgUnit;
import com.govinc.organization.OrgUnitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Optional;

@Controller
@RequestMapping("/users")
public class UserController {
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private UserSession userSession;
    
    @Autowired
    private AuthorizationService authorizationService;
    
    @Autowired
    private OrgUnitService orgUnitService;

    @Autowired
    private AssessmentRepository assessmentRepository;

    @GetMapping
    public String listUsers(Model model) {
        // Authorization check: only ADMIN can view users
        if (!authorizationService.isAdmin()) {
            throw new UnauthorizedException("You do not have permission to view users.");
        }
        List<User> users = userRepository.findAll();
        model.addAttribute("users", users);

        List<Assessment> assessments = assessmentRepository.findAll();
        Map<Long, List<Map<String, Object>>> userAssessmentsMap = new HashMap<>();
        Map<Long, List<Map<String, Object>>> createdByAssessmentsMap = new HashMap<>();

        for (Assessment assessment : assessments) {
            String assessmentName = assessment.getName();
            if (assessmentName == null || assessmentName.trim().isEmpty()) {
                assessmentName = "Assessment " + assessment.getId();
            }

            Map<String, Object> assessmentSummary = new HashMap<>();
            assessmentSummary.put("id", assessment.getId());
            assessmentSummary.put("name", assessmentName);

            if (assessment.getUsers() != null) {
                for (User assignedUser : assessment.getUsers()) {
                    if (assignedUser != null && assignedUser.getId() != null) {
                        userAssessmentsMap
                                .computeIfAbsent(assignedUser.getId(), k -> new ArrayList<>())
                                .add(assessmentSummary);
                    }
                }
            }

            if (assessment.getCreatedBy() != null && assessment.getCreatedBy().getId() != null) {
                createdByAssessmentsMap
                        .computeIfAbsent(assessment.getCreatedBy().getId(), k -> new ArrayList<>())
                        .add(assessmentSummary);
            }
        }

        model.addAttribute("userAssessmentsMap", userAssessmentsMap);
        model.addAttribute("createdByAssessmentsMap", createdByAssessmentsMap);

        // Add org units list for display purposes
        List<OrgUnit> orgUnits = orgUnitService.getAllOrgUnits();
        model.addAttribute("orgUnits", orgUnits);
        return "users";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        // Authorization check: only ADMIN can create users
        if (!authorizationService.isAdmin()) {
            throw new UnauthorizedException("You do not have permission to create users.");
        }
        model.addAttribute("user", new User());
        // Add org units list - will show only those not already assigned a leader
        List<OrgUnit> orgUnits = orgUnitService.getAllOrgUnits();
        model.addAttribute("orgUnits", orgUnits);
        return "user_form";
    }

    @PostMapping
    public String createUser(@ModelAttribute User user, @RequestParam(value = "leadOrgUnitIds", required = false) List<Long> leadOrgUnitIds) {
        // Authorization check: only ADMIN can create users
        if (!authorizationService.isAdmin()) {
            throw new UnauthorizedException("You do not have permission to create users.");
        }
        // Ensure the local admin account always retains the ADMIN role
        if (user.getEmail() != null && "admin@example.com".equalsIgnoreCase(user.getEmail())) {
            user.setRole(Role.ADMIN);
        }
        
        // Set team leader org units if provided and user is Organization Team Leader
        if (leadOrgUnitIds != null && !leadOrgUnitIds.isEmpty()) {
            if (Role.ORGANISATION_TEAM_LEADER == user.getRole()) {
                for (Long leadOrgUnitId : leadOrgUnitIds) {
                    Optional<OrgUnit> orgUnit = orgUnitService.getOrgUnit(leadOrgUnitId);
                    if (orgUnit.isPresent()) {
                        OrgUnit ou = orgUnit.get();
                        // Only assign if org unit doesn't already have a leader
                        if (ou.getLeader() == null) {
                            ou.setLeader(user);
                            orgUnitService.addOrgUnit(ou);
                            user.addLeadsOrgUnit(ou);
                        }
                    }
                }
            }
        }
        
        userRepository.save(user);
        return "redirect:/users";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        // Authorization check: only ADMIN can edit users
        if (!authorizationService.isAdmin()) {
            throw new UnauthorizedException("You do not have permission to edit users.");
        }
        Optional<User> user = userRepository.findById(id);
        if (user.isPresent()) {
            model.addAttribute("user", user.get());
            // Add org units list
            List<OrgUnit> orgUnits = orgUnitService.getAllOrgUnits();
            model.addAttribute("orgUnits", orgUnits);
            return "user_form";
        } else {
            return "redirect:/users";
        }
    }

    @PostMapping("/update/{id}")
    public String updateUser(@PathVariable Long id, @ModelAttribute User user, @RequestParam(value = "leadOrgUnitIds", required = false) List<Long> leadOrgUnitIds) {
        // Authorization check: only ADMIN can update users
        if (!authorizationService.isAdmin()) {
            throw new UnauthorizedException("You do not have permission to update users.");
        }
        user.setId(id);

        // Get the current authenticated user
        User currentUser = authorizationService.getCurrentUser();

        // Ensure the local admin account always retains the ADMIN role
        if (user.getEmail() != null && "admin@example.com".equalsIgnoreCase(user.getEmail())) {
            user.setRole(Role.ADMIN);
        }

        // Prevent an ADMIN user from removing their own ADMIN role
        if (currentUser != null && currentUser.getId().equals(id) && (currentUser.getRole() == Role.ADMIN)) {
            user.setRole(Role.ADMIN);
        }
        
        // Get the current user from database
        Optional<User> existingUserOpt = userRepository.findById(id);
        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();
            
            // If current role is Team Leader, remove from any org units they currently lead
            if (Role.ORGANISATION_TEAM_LEADER == existingUser.getRole()) {
                for (OrgUnit orgUnit : existingUser.getLeadsOrgUnits()) {
                    orgUnit.setLeader(null);
                    orgUnitService.addOrgUnit(orgUnit);
                }
            }
            
            // Set new team leader org units if provided
            if (leadOrgUnitIds != null && !leadOrgUnitIds.isEmpty()) {
                if (Role.ORGANISATION_TEAM_LEADER == user.getRole()) {
                    for (Long leadOrgUnitId : leadOrgUnitIds) {
                        Optional<OrgUnit> orgUnit = orgUnitService.getOrgUnit(leadOrgUnitId);
                        if (orgUnit.isPresent()) {
                            OrgUnit ou = orgUnit.get();
                            // Only assign if org unit doesn't have a different leader
                            if (ou.getLeader() == null || ou.getLeader().getId().equals(user.getId())) {
                                ou.setLeader(user);
                                orgUnitService.addOrgUnit(ou);
                                user.addLeadsOrgUnit(ou);
                            }
                        }
                    }
                }
            }
        }
        
        userRepository.save(user);
        return "redirect:/users";
    }

    @GetMapping("/delete/{id}")
    public String deleteUser(@PathVariable Long id) {
        // Authorization check: only ADMIN can delete users
        if (!authorizationService.isAdmin()) {
            throw new UnauthorizedException("You do not have permission to delete users.");
        }

        // Get the current authenticated user
        User currentUser = authorizationService.getCurrentUser();

        // Prevent ADMIN users from deleting themselves
        if (currentUser != null && currentUser.getId().equals(id)) {
            throw new UnauthorizedException("You cannot delete your own user account.");
        }

        // A user cannot be deleted when referenced as createdBy in assessments.
        List<Assessment> createdByAssessments = assessmentRepository.findByCreatedById(id);
        if (!createdByAssessments.isEmpty()) {
            throw new UnauthorizedException("You cannot delete this user because they are the creator of one or more assessments.");
        }

        // Remove user from the assessment users relation before deletion.
        List<Assessment> assignedAssessments = assessmentRepository.findByUsersId(id);
        if (!assignedAssessments.isEmpty()) {
            Optional<User> userToDeleteOpt = userRepository.findById(id);
            if (userToDeleteOpt.isPresent()) {
                User userToDelete = userToDeleteOpt.get();
                for (Assessment assessment : assignedAssessments) {
                    if (assessment.getUsers() != null && assessment.getUsers().remove(userToDelete)) {
                        assessmentRepository.save(assessment);
                    }
                }
            }
        }

        // Remove user from any org units they lead
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            for (OrgUnit orgUnit : user.getLeadsOrgUnits()) {
                orgUnit.setLeader(null);
                orgUnitService.addOrgUnit(orgUnit);
            }
        }

        userRepository.deleteById(id);
        return "redirect:/users";
    }

    // --- SESSION USER SETTING ----
    @PostMapping("/set-session-user")
    public String setSessionUser(@RequestParam("userId") String userId, @RequestParam(value = "redirect", required = false) String redirect) {
        userSession.setUserId(userId);
        if (redirect != null && !redirect.isEmpty()) {
            return "redirect:" + redirect;
        }
        return "redirect:/";
    }

    // Endpoint for API to get all users as JSON
    @GetMapping("/api")
    @ResponseBody
    public List<User> apiGetAllUsers() {
        // Authorization check: ADMIN and ISM can view users
        if (!authorizationService.isInformationSecurityManager()) {
            throw new UnauthorizedException("You do not have permission to view users.");
        }
        return userRepository.findAll();
    }
    
    // Endpoint to get all org units for user assignment
    @GetMapping("/api/orgUnits")
    @ResponseBody
    public List<OrgUnit> apiGetOrgUnits() {
        // Authorization check: ADMIN and ISM can access
        if (!authorizationService.isInformationSecurityManager()) {
            throw new UnauthorizedException("You do not have permission to access org units.");
        }
        return orgUnitService.getAllOrgUnits();
    }

    // Find a local user by email (used by org-unit team lead corporate directory picker)
    @GetMapping("/api/find-by-email")
    @ResponseBody
    public Map<String, Object> apiFindByEmail(@RequestParam("email") String email) {
        if (!(authorizationService.isAdmin()
                || authorizationService.isInformationSecurityManager()
                || authorizationService.isAssessor())) {
            throw new UnauthorizedException("You do not have permission to look up users.");
        }
        Optional<User> found = userRepository.findByEmail(email.trim().toLowerCase());
        if (found.isPresent()) {
            User u = found.get();
            Map<String, Object> out = new HashMap<>();
            out.put("id", u.getId());
            out.put("name", u.getName());
            out.put("email", u.getEmail());
            return out;
        }
        return Map.of("notFound", true);
    }

    // Endpoint to get current logged-in user's name and email
    @GetMapping("/me")
    @ResponseBody
    public java.util.Map<String, Object> getCurrentUser(org.springframework.security.core.Authentication authentication) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        if (authentication == null) {
            result.put("name", "anonymous");
            return result;
        }
        result.put("name", authentication.getName());
        // Try to extract email and name fields (for OIDC providers)
        if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.oidc.user.OidcUser oidcUser) {
            result.put("email", oidcUser.getEmail());
            result.put("firstName", oidcUser.getGivenName());
            result.put("lastName", oidcUser.getFamilyName());
        } else if (authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.User user) {
            // Plain local user
            result.put("email", authentication.getName() + "@local");
            result.put("firstName", authentication.getName());
            result.put("lastName", null);
        } else {
            // Last resort: generic principal
            result.put("email", null);
            result.put("firstName", null);
            result.put("lastName", null);
        }
        return result;
    }
}
