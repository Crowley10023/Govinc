package com.govinc.governance;

import com.govinc.authorization.AuthorizationService;
import com.govinc.catalog.SecurityCatalogRepository;
import com.govinc.catalog.SecurityControlDomainRepository;
import com.govinc.catalog.SecurityControlRepository;
import com.govinc.user.User;
import com.govinc.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/governance/tasks")
public class GovernanceTaskController {

    @Autowired
    private GovernanceTaskService taskService;

    @Autowired
    private GovernanceProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SecurityControlRepository securityControlRepository;

    @Autowired
    private SecurityCatalogRepository securityCatalogRepository;

    @Autowired
    private SecurityControlDomainRepository securityControlDomainRepository;

    @Autowired
    private AuthorizationService authorizationService;

    @GetMapping("")
    public String listTasks(Model model) {
        model.addAttribute("tasks", taskService.findAll());
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("projects", projectRepository.findAll());
        model.addAttribute("securityControls", securityControlRepository.findAll());
        model.addAttribute("securityCatalogs", securityCatalogRepository.findAll());
        model.addAttribute("securityControlDomains", securityControlDomainRepository.findAll());
        model.addAttribute("statuses", TaskStatus.values());
        return "governance-tasks";
    }

    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<?> createTask(@RequestBody Map<String, Object> payload) {
        User currentUser = authorizationService.getCurrentUser();

        String title = (String) payload.get("title");
        String description = (String) payload.get("description");
        Long assignedUserId = payload.get("assignedUserId") != null ? Long.valueOf(payload.get("assignedUserId").toString()) : null;
        Long securityControlId = payload.get("securityControlId") != null ? Long.valueOf(payload.get("securityControlId").toString()) : null;
        Long securityCatalogId = payload.get("securityCatalogId") != null ? Long.valueOf(payload.get("securityCatalogId").toString()) : null;
        Long securityControlDomainId = payload.get("securityControlDomainId") != null ? Long.valueOf(payload.get("securityControlDomainId").toString()) : null;
        Long projectId = payload.get("projectId") != null ? Long.valueOf(payload.get("projectId").toString()) : null;
        String dueDate = (String) payload.get("dueDate");

        GovernanceTask task = taskService.createTask(title, description, assignedUserId,
                securityControlId, securityCatalogId, securityControlDomainId, projectId, dueDate, currentUser);

        return ResponseEntity.ok(Map.of("id", task.getId(), "status", "created"));
    }

    @PutMapping("/{id}/status")
    @ResponseBody
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        String statusStr = payload.get("status");
        TaskStatus status = TaskStatus.valueOf(statusStr);
        taskService.updateStatus(id, status);
        return ResponseEntity.ok(Map.of("status", "updated"));
    }

    @PutMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> updateTask(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Optional<GovernanceTask> taskOpt = taskService.findById(id);
        if (taskOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        GovernanceTask task = taskOpt.get();

        if (payload.containsKey("title")) task.setTitle((String) payload.get("title"));
        if (payload.containsKey("description")) task.setDescription((String) payload.get("description"));
        if (payload.containsKey("status")) task.setStatus(TaskStatus.valueOf((String) payload.get("status")));
        if (payload.containsKey("assignedUserId")) {
            Long uid = payload.get("assignedUserId") != null ? Long.valueOf(payload.get("assignedUserId").toString()) : null;
            task.setAssignedUser(uid != null ? userRepository.findById(uid).orElse(null) : null);
        }
        if (payload.containsKey("projectId")) {
            Long pid = payload.get("projectId") != null ? Long.valueOf(payload.get("projectId").toString()) : null;
            task.setProject(pid != null ? projectRepository.findById(pid).orElse(null) : null);
        }
        if (payload.containsKey("dueDate")) {
            String dd = (String) payload.get("dueDate");
            task.setDueDate(dd != null && !dd.isBlank() ? java.time.LocalDate.parse(dd) : null);
        }

        taskService.save(task);
        return ResponseEntity.ok(Map.of("status", "updated"));
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteTask(@PathVariable Long id) {
        taskService.delete(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }
}
