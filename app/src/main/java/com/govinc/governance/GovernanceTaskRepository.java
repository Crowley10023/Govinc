package com.govinc.governance;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GovernanceTaskRepository extends JpaRepository<GovernanceTask, Long> {
    List<GovernanceTask> findByAssignedUserId(Long userId);
    List<GovernanceTask> findByProjectId(Long projectId);
    List<GovernanceTask> findBySecurityControlId(Long controlId);
    List<GovernanceTask> findBySecurityCatalogId(Long catalogId);
    List<GovernanceTask> findByStatus(TaskStatus status);
}
