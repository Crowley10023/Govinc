package com.govinc.governance;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GovernanceProjectRepository extends JpaRepository<GovernanceProject, Long> {
    List<GovernanceProject> findByOwnerId(Long ownerId);
    List<GovernanceProject> findByProjectType(ProjectType projectType);
}
