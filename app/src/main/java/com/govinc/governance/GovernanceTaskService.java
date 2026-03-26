package com.govinc.governance;

import com.govinc.catalog.SecurityCatalog;
import com.govinc.catalog.SecurityCatalogRepository;
import com.govinc.catalog.SecurityControl;
import com.govinc.catalog.SecurityControlRepository;
import com.govinc.catalog.SecurityControlDomain;
import com.govinc.catalog.SecurityControlDomainRepository;
import com.govinc.user.User;
import com.govinc.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class GovernanceTaskService {

    @Autowired
    private GovernanceTaskRepository taskRepository;

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

    public List<GovernanceTask> findAll() {
        return taskRepository.findAll();
    }

    public Optional<GovernanceTask> findById(Long id) {
        return taskRepository.findById(id);
    }

    public List<GovernanceTask> findByAssignedUser(Long userId) {
        return taskRepository.findByAssignedUserId(userId);
    }

    public List<GovernanceTask> findByProject(Long projectId) {
        return taskRepository.findByProjectId(projectId);
    }

    public GovernanceTask save(GovernanceTask task) {
        return taskRepository.save(task);
    }

    public GovernanceTask createTask(String title, String description, Long assignedUserId,
                                     Long securityControlId, Long securityCatalogId,
                                     Long securityControlDomainId, Long projectId,
                                     String dueDateStr, User createdBy) {
        GovernanceTask task = new GovernanceTask();
        task.setTitle(title);
        task.setDescription(description);
        task.setStatus(TaskStatus.IDENTIFIED);
        task.setCreatedBy(createdBy);

        if (assignedUserId != null) {
            userRepository.findById(assignedUserId).ifPresent(task::setAssignedUser);
        }
        if (securityControlId != null) {
            securityControlRepository.findById(securityControlId).ifPresent(task::setSecurityControl);
        }
        if (securityCatalogId != null) {
            securityCatalogRepository.findById(securityCatalogId).ifPresent(task::setSecurityCatalog);
        }
        if (securityControlDomainId != null) {
            securityControlDomainRepository.findById(securityControlDomainId).ifPresent(task::setSecurityControlDomain);
        }
        if (projectId != null) {
            projectRepository.findById(projectId).ifPresent(task::setProject);
        }
        if (dueDateStr != null && !dueDateStr.isBlank()) {
            task.setDueDate(java.time.LocalDate.parse(dueDateStr));
        }

        return taskRepository.save(task);
    }

    public void updateStatus(Long taskId, TaskStatus status) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setStatus(status);
            taskRepository.save(task);
        });
    }

    public void delete(Long id) {
        taskRepository.deleteById(id);
    }
}
