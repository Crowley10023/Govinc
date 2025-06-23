package com.govinc.assessment;

import com.govinc.organization.OrgService;
import com.govinc.organization.OrgServiceService;
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

    @Autowired
    public AssessmentRestController(AssessmentRepository assessmentRepository, OrgServiceService orgServiceService) {
        this.assessmentRepository = assessmentRepository;
        this.orgServiceService = orgServiceService;
    }

    @PutMapping("/{id}/orgservices")
    public void updateOrgServices(@PathVariable Long id, @RequestBody List<Long> orgServiceIds) {
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
        return assessmentRepository.findById(id)
                .map(a -> a.getOrgServices().stream().map(OrgService::getId).collect(Collectors.toList()))
                .orElse(java.util.Collections.emptyList());
    }
}

