package com.govinc.governance;

import com.govinc.authorization.AuthorizationService;
import com.govinc.catalog.SecurityCatalog;
import com.govinc.catalog.SecurityCatalogRepository;
import com.govinc.compliance.ComplianceCheck;
import com.govinc.compliance.ComplianceService;
import com.govinc.organization.OrgUnit;
import com.govinc.organization.OrgUnitService;
import com.govinc.user.User;
import com.govinc.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
@RequestMapping("/governance/deviation-analysis")
public class DeviationAnalysisController {

    @Autowired
    private DeviationAnalysisService deviationAnalysisService;

    @Autowired
    private OrgUnitService orgUnitService;

    @Autowired
    private SecurityCatalogRepository securityCatalogRepository;

    @Autowired
    private ComplianceService complianceService;

    @Autowired
    private GovernanceTaskService taskService;

    @Autowired
    private GovernanceProjectRepository projectRepository;

    @Autowired
    private GovernanceProjectService projectService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthorizationService authorizationService;

    @GetMapping("")
    public String showAnalysis(@RequestParam(required = false) Long orgUnitId,
                               @RequestParam(required = false) Long catalogId,
                               @RequestParam(required = false) Long checkId,
                               Model model) {
        model.addAttribute("orgUnits", orgUnitService.getAllOrgUnits());
        model.addAttribute("catalogs", securityCatalogRepository.findAll());
        model.addAttribute("complianceChecks", complianceService.findAll());
        model.addAttribute("projects", projectRepository.findAll());
        model.addAttribute("users", userRepository.findAll());

        if (orgUnitId != null && catalogId != null && checkId != null) {
            OrgUnit selectedOrg = orgUnitService.getOrgUnit(orgUnitId).orElse(null);
            SecurityCatalog selectedCatalog = securityCatalogRepository.findById(catalogId).orElse(null);
            ComplianceCheck selectedCheck = complianceService.findById(checkId).orElse(null);

            model.addAttribute("selectedOrgUnitId", orgUnitId);
            model.addAttribute("selectedCatalogId", catalogId);
            model.addAttribute("selectedCheckId", checkId);

            if (selectedOrg != null && selectedCatalog != null && selectedCheck != null) {
                List<DeviationAnalysisService.GapItem> gaps =
                        deviationAnalysisService.analyzeDeviations(selectedOrg, selectedCatalog, selectedCheck);
                model.addAttribute("gaps", gaps);
                model.addAttribute("selectedOrgName", selectedOrg.getName());
                model.addAttribute("selectedCatalogName", selectedCatalog.getName());
                model.addAttribute("selectedCheckName", selectedCheck.getName());
            }
        }

        return "governance-deviation-analysis";
    }

    @PostMapping("/create-task")
    @ResponseBody
    public ResponseEntity<?> createTaskFromGap(@RequestBody Map<String, Object> payload) {
        User currentUser = authorizationService.getCurrentUser();

        String title = (String) payload.get("title");
        String description = (String) payload.get("description");
        Long assignedUserId = payload.get("assignedUserId") != null ? Long.valueOf(payload.get("assignedUserId").toString()) : null;
        Long projectId = payload.get("projectId") != null ? Long.valueOf(payload.get("projectId").toString()) : null;
        Long securityControlId = payload.get("securityControlId") != null ? Long.valueOf(payload.get("securityControlId").toString()) : null;

        GovernanceTask task = taskService.createTask(title, description, assignedUserId,
                securityControlId, null, null, projectId, null, currentUser);

        return ResponseEntity.ok(Map.of("id", task.getId(), "status", "created"));
    }

    @PostMapping("/create-all-tasks")
    @ResponseBody
    public ResponseEntity<?> createAllTasksFromGap(@RequestBody Map<String, Object> payload) {
        User currentUser = authorizationService.getCurrentUser();

        Long assignedUserId = payload.get("assignedUserId") != null ? Long.valueOf(payload.get("assignedUserId").toString()) : null;
        String newProjectName = (String) payload.get("newProjectName");
        Long projectId = payload.get("projectId") != null ? Long.valueOf(payload.get("projectId").toString()) : null;

        // Create new project if requested
        if (newProjectName != null && !newProjectName.isBlank()) {
            GovernanceProject newProject = projectService.createProject(newProjectName.trim(), null, assignedUserId, currentUser);
            projectId = newProject.getId();
        }

        if (projectId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "A project is required"));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) payload.get("tasks");
        if (tasks == null || tasks.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No tasks provided"));
        }

        int created = 0;
        for (Map<String, Object> t : tasks) {
            String title = (String) t.get("title");
            String description = (String) t.get("description");
            Long controlId = t.get("securityControlId") != null && !t.get("securityControlId").toString().isBlank()
                    ? Long.valueOf(t.get("securityControlId").toString()) : null;
            if (title != null && !title.isBlank()) {
                taskService.createTask(title, description, assignedUserId, controlId, null, null, projectId, null, currentUser);
                created++;
            }
        }

        return ResponseEntity.ok(Map.of("created", created, "projectId", projectId));
    }
}
