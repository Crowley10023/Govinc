package com.govinc.governance;

import com.govinc.authorization.AuthorizationService;
import com.govinc.user.User;
import com.govinc.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.ArrayList;

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

    @GetMapping("")
    public String listProjects(Model model) {
        model.addAttribute("projects", projectService.findAll());
        model.addAttribute("users", userRepository.findAll());
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

        if (payload.containsKey("trackChanges")) {
            project.setTrackChanges(Boolean.TRUE.equals(payload.get("trackChanges")));
            projectService.save(project);
        }

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
        if (payload.containsKey("trackChanges")) {
            project.setTrackChanges(Boolean.TRUE.equals(payload.get("trackChanges")));
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
            // Include historic data for expand/collapse detail
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
            // Current control data
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
}
