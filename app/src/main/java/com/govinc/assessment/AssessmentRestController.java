package com.govinc.assessment;

import com.govinc.organization.OrgService;
import com.govinc.organization.OrgServiceService;
import com.govinc.authorization.AuthorizationService;
import com.govinc.authorization.UnauthorizedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/assessment")
public class AssessmentRestController {
    private final AssessmentRepository assessmentRepository;
    private final OrgServiceService orgServiceService;
    private final AuthorizationService authorizationService;

    @Autowired
    public AssessmentRestController(AssessmentRepository assessmentRepository, OrgServiceService orgServiceService, AuthorizationService authorizationService) {
        this.assessmentRepository = assessmentRepository;
        this.orgServiceService = orgServiceService;
        this.authorizationService = authorizationService;
    }

    @PutMapping("/{id}/orgservices")
    public void updateOrgServices(@PathVariable Long id, @RequestBody List<Long> orgServiceIds) {
        // Authorization check: user must be able to modify the assessment
        if (!authorizationService.canModifyAssessment(id)) {
            throw new UnauthorizedException("You do not have permission to modify this assessment's org services.");
        }
        
        System.out.println("[REST] updateOrgServices called for Assessment ID: " + id + " with OrgServiceIds: " + orgServiceIds);
        Optional<Assessment> assessmentOpt = assessmentRepository.findById(id);
        if (assessmentOpt.isPresent()) {
            Assessment assessment = assessmentOpt.get();
            Set<OrgService> orgServices = orgServiceIds.stream()
                .map(orgServiceService::getOrgService)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
            assessment.setOrgServices(orgServices);
            assessmentRepository.save(assessment);
            System.out.println("[REST] Updated assessment " + id + " now has assigned OrgServices: " + assessment.getOrgServices());
        } else {
            System.out.println("[REST] Assessment ID not found: " + id);
        }
    }

    // New endpoint: get assigned orgservice ids for an assessment
    @GetMapping("/{id}/orgservice-ids")
    public List<Long> assignedOrgServiceIds(@PathVariable Long id) {
        // Authorization check: user must be able to access the assessment
        if (!authorizationService.canAccessAssessment(id)) {
            throw new UnauthorizedException("You do not have permission to access this assessment.");
        }
        
        return assessmentRepository.findById(id)
                .map(a -> a.getOrgServices().stream().map(OrgService::getId).collect(Collectors.toList()))
                .orElse(java.util.Collections.emptyList());
    }

    // Endpoint to get all org services (accessible to authenticated users for assessment context)
    // This bypasses the /orgservices/** security restriction to allow team leaders and delegates to see org services
    @GetMapping("/all-orgservices")
    public List<OrgService> getAllOrgServices() {
        // This endpoint is accessible to all authenticated users
        // Authorization for modifying an assessment's org services is checked in updateOrgServices()
        return orgServiceService.getAllOrgServices();
    }
}
