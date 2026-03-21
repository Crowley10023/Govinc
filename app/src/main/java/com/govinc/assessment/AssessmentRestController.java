package com.govinc.assessment;

import com.govinc.compliance.ComplianceCheck;
import com.govinc.compliance.ComplianceCheckRepository;
import com.govinc.organization.OrgService;
import com.govinc.organization.OrgServiceService;
import com.govinc.authorization.AuthorizationService;
import com.govinc.authorization.UnauthorizedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/assessment")
public class AssessmentRestController {
    private final AssessmentRepository assessmentRepository;
    private final OrgServiceService orgServiceService;
    private final AuthorizationService authorizationService;
    private final ComplianceCheckRepository complianceCheckRepository;

    @Autowired
    public AssessmentRestController(
            AssessmentRepository assessmentRepository,
            OrgServiceService orgServiceService,
            AuthorizationService authorizationService,
            ComplianceCheckRepository complianceCheckRepository) {
        this.assessmentRepository = assessmentRepository;
        this.orgServiceService = orgServiceService;
        this.authorizationService = authorizationService;
        this.complianceCheckRepository = complianceCheckRepository;
    }

    @PutMapping("/{id}/compliance-check")
    public Map<String, Object> updateComplianceCheck(@PathVariable Long id,
            @RequestBody(required = false) Map<String, Long> payload) {
        if (!authorizationService.canCreateAssessment()) {
            throw new UnauthorizedException("You do not have permission to update this assessment's compliance check.");
        }

        Assessment assessment = assessmentRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND));

        Long complianceCheckId = payload != null ? payload.get("complianceCheckId") : null;
        ComplianceCheck complianceCheck = null;
        if (complianceCheckId != null) {
            complianceCheck = complianceCheckRepository.findById(complianceCheckId)
                    .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.BAD_REQUEST,
                            "Compliance check not found."));

            Long assessmentCatalogId = assessment.getSecurityCatalog() != null ? assessment.getSecurityCatalog().getId() : null;
            Long complianceCatalogId = complianceCheck.getSecurityCatalog() != null ? complianceCheck.getSecurityCatalog().getId() : null;
            if (assessmentCatalogId == null || !assessmentCatalogId.equals(complianceCatalogId)) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Compliance check does not belong to this assessment's security catalog.");
            }
        }

        assessment.setComplianceCheck(complianceCheck);
        assessmentRepository.save(assessment);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", complianceCheck != null ? complianceCheck.getId() : null);
        response.put("name", complianceCheck != null ? complianceCheck.getName() : null);
        return response;
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
        if (authorizationService.isAssessor()) {
            throw new UnauthorizedException("Assessors are not allowed to manage org services.");
        }
        return orgServiceService.getAllOrgServices();
    }
}
