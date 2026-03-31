package com.govinc.governance;

import com.govinc.assessment.Assessment;
import com.govinc.authorization.AuthorizationService;
import com.govinc.catalog.SecurityCatalogRepository;
import com.govinc.organization.OrgUnitService;
import com.govinc.user.User;
import com.govinc.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/governance/projects")
public class GovernanceProjectController {

    @Autowired
    private GovernanceProjectService projectService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private SecurityControlChangeTrackingRepository changeTrackingRepository;

    @Autowired
    private SecurityCatalogRepository securityCatalogRepository;

    @Autowired
    private OrgUnitService orgUnitService;

    @GetMapping("")
    public String listProjects(Model model) {
        model.addAttribute("projects", projectService.findAll());
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("projectTypes", ProjectType.values());
        model.addAttribute("catalogs", securityCatalogRepository.findAll());
        return "governance-projects";
    }

    @GetMapping("/{id}")
    public String viewProject(@PathVariable Long id, Model model) {
        Optional<GovernanceProject> projectOpt = projectService.findById(id);
        if (projectOpt.isEmpty()) {
            return "redirect:/governance/projects";
        }
        model.addAttribute("project", projectOpt.get());
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("statuses", TaskStatus.values());
        model.addAttribute("catalogs", securityCatalogRepository.findAll());
        return "governance-project-detail";
    }

    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<?> createProject(@RequestBody Map<String, Object> payload) {
        User currentUser = authorizationService.getCurrentUser();

        String name = (String) payload.get("name");
        String description = (String) payload.get("description");
        Long ownerId = payload.get("ownerId") != null ? Long.valueOf(payload.get("ownerId").toString()) : null;

        GovernanceProject project = projectService.createProject(name, description, ownerId, currentUser);

        // Set project type
        String typeStr = (String) payload.get("projectType");
        if (typeStr != null && !typeStr.isBlank()) {
            try {
                project.setProjectType(ProjectType.valueOf(typeStr));
            } catch (IllegalArgumentException ignored) {}
        }

        // Track changes only allowed for CHANGE_MANAGEMENT
        if (project.getProjectType() == ProjectType.CHANGE_MANAGEMENT && payload.containsKey("trackChanges")) {
            project.setTrackChanges(Boolean.TRUE.equals(payload.get("trackChanges")));
        } else {
            project.setTrackChanges(false);
        }

        projectService.save(project);

        return ResponseEntity.ok(Map.of("id", project.getId(), "status", "created"));
    }

    @PutMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> updateProject(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Optional<GovernanceProject> projectOpt = projectService.findById(id);
        if (projectOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        GovernanceProject project = projectOpt.get();

        if (payload.containsKey("name")) project.setName((String) payload.get("name"));
        if (payload.containsKey("description")) project.setDescription((String) payload.get("description"));
        if (payload.containsKey("ownerId")) {
            Long oid = payload.get("ownerId") != null ? Long.valueOf(payload.get("ownerId").toString()) : null;
            project.setOwner(oid != null ? userRepository.findById(oid).orElse(null) : null);
        }
        if (payload.containsKey("projectType")) {
            String typeStr = (String) payload.get("projectType");
            if (typeStr != null && !typeStr.isBlank()) {
                try {
                    project.setProjectType(ProjectType.valueOf(typeStr));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        // Track changes only allowed for CHANGE_MANAGEMENT
        if (project.getProjectType() == ProjectType.CHANGE_MANAGEMENT && payload.containsKey("trackChanges")) {
            project.setTrackChanges(Boolean.TRUE.equals(payload.get("trackChanges")));
        } else if (project.getProjectType() != ProjectType.CHANGE_MANAGEMENT) {
            project.setTrackChanges(false);
        }

        projectService.save(project);
        return ResponseEntity.ok(Map.of("status", "updated"));
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteProject(@PathVariable Long id) {
        projectService.delete(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @GetMapping("/{id}/changes")
    @ResponseBody
    public ResponseEntity<?> getProjectChanges(@PathVariable Long id) {
        Optional<GovernanceProject> projectOpt = projectService.findById(id);
        if (projectOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<SecurityControlChangeTracking> changes = changeTrackingRepository
            .findByGovernanceProjectIdOrderByChangedAtDesc(id);
        List<Map<String, Object>> result = new ArrayList<>();
        for (SecurityControlChangeTracking ct : changes) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("id", ct.getId());
            entry.put("controlName", ct.getSecurityControl().getName());
            entry.put("controlId", ct.getSecurityControl().getId());
            entry.put("fromVersion", ct.getFromVersion());
            entry.put("toVersion", ct.getToVersion());
            entry.put("changedAt", ct.getChangedAt() != null ? ct.getChangedAt().toString() : null);
            entry.put("changedBy", ct.getChangedBy() != null ? ct.getChangedBy().getName() : null);
            var prev = ct.getPreviousVersion();
            if (prev != null) {
                Map<String, Object> prevData = new HashMap<>();
                prevData.put("name", prev.getName());
                prevData.put("detail", prev.getDetail());
                prevData.put("reference", prev.getReference());
                prevData.put("tag", prev.getTag());
                prevData.put("domain", prev.getSecurityControlDomain() != null ? prev.getSecurityControlDomain().getName() : null);
                entry.put("previousData", prevData);
            }
            Map<String, Object> currentData = new HashMap<>();
            currentData.put("name", ct.getSecurityControl().getName());
            currentData.put("detail", ct.getSecurityControl().getDetail());
            currentData.put("reference", ct.getSecurityControl().getReference());
            currentData.put("tag", ct.getSecurityControl().getTag());
            currentData.put("domain", ct.getSecurityControl().getSecurityControlDomain() != null ? ct.getSecurityControl().getSecurityControlDomain().getName() : null);
            entry.put("currentData", currentData);
            result.add(entry);
        }
        return ResponseEntity.ok(result);
    }

    // --- Linked Assessments Management for Deviation Management projects ---

    @GetMapping("/{id}/linked-assessments")
    @ResponseBody
    public ResponseEntity<?> getLinkedAssessments(@PathVariable Long id) {
        Optional<GovernanceProject> projectOpt = projectService.findById(id);
        if (projectOpt.isEmpty()) return ResponseEntity.notFound().build();

        GovernanceProject project = projectOpt.get();
        List<Map<String, Object>> result = project.getLinkedAssessments().stream()
            .sorted(Comparator.comparing(Assessment::getName, String.CASE_INSENSITIVE_ORDER))
            .map(a -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", a.getId());
                m.put("name", a.getName());
                m.put("orgUnit", a.getOrgUnit() != null ? a.getOrgUnit().getName() : "-");
                m.put("catalog", a.getSecurityCatalog() != null ? a.getSecurityCatalog().getName() : "-");
                m.put("status", a.getStatus() != null ? a.getStatus().name() : "-");
                m.put("creationDate", a.getCreationDate() != null ? a.getCreationDate().toString() : "-");
                return m;
            })
            .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/link-assessments")
    @ResponseBody
    public ResponseEntity<?> linkAssessments(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Optional<GovernanceProject> projectOpt = projectService.findById(id);
        if (projectOpt.isEmpty()) return ResponseEntity.notFound().build();

        GovernanceProject project = projectOpt.get();
        if (project.getProjectType() != ProjectType.DEVIATION_MANAGEMENT) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only Deviation Management projects support linked assessments"));
        }

        Long orgUnitId = payload.get("orgUnitId") != null ? Long.valueOf(payload.get("orgUnitId").toString()) : null;
        Long catalogId = payload.get("catalogId") != null ? Long.valueOf(payload.get("catalogId").toString()) : null;

        if (orgUnitId == null || catalogId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Org unit and catalog are required"));
        }

        Set<Assessment> linked = projectService.linkLatestAssessments(project, orgUnitId, catalogId);
        return ResponseEntity.ok(Map.of("linked", linked.size()));
    }

    @PostMapping("/{id}/unlink-assessment")
    @ResponseBody
    public ResponseEntity<?> unlinkAssessment(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Optional<GovernanceProject> projectOpt = projectService.findById(id);
        if (projectOpt.isEmpty()) return ResponseEntity.notFound().build();

        Long assessmentId = payload.get("assessmentId") != null ? Long.valueOf(payload.get("assessmentId").toString()) : null;
        if (assessmentId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Assessment ID is required"));
        }

        projectService.unlinkAssessment(projectOpt.get(), assessmentId);
        return ResponseEntity.ok(Map.of("status", "unlinked"));
    }
}
