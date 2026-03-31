package com.govinc.governance;

import com.govinc.assessment.Assessment;
import com.govinc.assessment.AssessmentRepository;
import com.govinc.catalog.SecurityCatalog;
import com.govinc.catalog.SecurityCatalogRepository;
import com.govinc.compliance.ComplianceService;
import com.govinc.organization.OrgUnit;
import com.govinc.organization.OrgUnitService;
import com.govinc.user.User;
import com.govinc.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GovernanceProjectService {

    @Autowired
    private GovernanceProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AssessmentRepository assessmentRepository;

    @Autowired
    private OrgUnitService orgUnitService;

    @Autowired
    private SecurityCatalogRepository securityCatalogRepository;

    @Autowired
    private ComplianceService complianceService;

    public List<GovernanceProject> findAll() {
        return projectRepository.findAll();
    }

    public Optional<GovernanceProject> findById(Long id) {
        return projectRepository.findById(id);
    }

    public List<GovernanceProject> findByProjectType(ProjectType type) {
        return projectRepository.findByProjectType(type);
    }

    public GovernanceProject createProject(String name, String description, Long ownerId, User createdBy) {
        GovernanceProject project = new GovernanceProject();
        project.setName(name);
        project.setDescription(description);
        project.setCreatedBy(createdBy);

        if (ownerId != null) {
            userRepository.findById(ownerId).ifPresent(project::setOwner);
        }

        return projectRepository.save(project);
    }

    public GovernanceProject save(GovernanceProject project) {
        return projectRepository.save(project);
    }

    public void delete(Long id) {
        projectRepository.deleteById(id);
    }

    /**
     * Link the latest assessments for an org unit (and its children) and a given catalog
     * to a Deviation Management project.
     */
    public Set<Assessment> linkLatestAssessments(GovernanceProject project, Long orgUnitId, Long catalogId) {
        OrgUnit orgUnit = orgUnitService.getOrgUnit(orgUnitId).orElse(null);
        SecurityCatalog catalog = securityCatalogRepository.findById(catalogId).orElse(null);
        if (orgUnit == null || catalog == null) return Collections.emptySet();

        List<OrgUnit> units = complianceService.collectWithChildren(orgUnit);
        Map<Long, Assessment> latestMap = complianceService.getLatestAssessments(units, catalog);

        Set<Assessment> linked = new HashSet<>(latestMap.values());
        project.getLinkedAssessments().addAll(linked);
        projectRepository.save(project);
        return linked;
    }

    /**
     * Remove a linked assessment from a project.
     */
    public void unlinkAssessment(GovernanceProject project, Long assessmentId) {
        project.getLinkedAssessments().removeIf(a -> a.getId().equals(assessmentId));
        projectRepository.save(project);
    }

    /**
     * Add a single assessment to a project's linked assessments.
     */
    public void linkAssessment(GovernanceProject project, Long assessmentId) {
        assessmentRepository.findById(assessmentId).ifPresent(a -> {
            project.getLinkedAssessments().add(a);
            projectRepository.save(project);
        });
    }
}
