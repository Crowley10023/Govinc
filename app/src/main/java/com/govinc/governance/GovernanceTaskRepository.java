package com.govinc.governance;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GovernanceTaskRepository extends JpaRepository<GovernanceTask, Long> {
    List<GovernanceTask> findByAssignedUserId(Long userId);
    List<GovernanceTask> findByProjectId(Long projectId);
    List<GovernanceTask> findBySecurityControlId(Long controlId);
    List<GovernanceTask> findBySecurityCatalogId(Long catalogId);
    List<GovernanceTask> findByStatus(TaskStatus status);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE GovernanceTask task SET task.assessment = null WHERE task.assessment.id = :assessmentId")
    void clearAssessmentReferences(@org.springframework.data.repository.query.Param("assessmentId") Long assessmentId);
}
