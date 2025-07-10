package com.govinc.organization;

import com.govinc.catalog.SecurityControl;
import com.govinc.catalog.SecurityControlRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrgServiceAssessmentService {
    private final OrgServiceAssessmentRepository assessmentRepository;
    private final OrgServiceAssessmentControlRepository controlRepository;
    private final SecurityControlRepository securityControlRepository;
    private final OrgServiceRepository orgServiceRepository;
    private final OrgServiceService orgServiceService;

    public OrgServiceAssessmentService(OrgServiceAssessmentRepository assessmentRepository,
                                       OrgServiceAssessmentControlRepository controlRepository,
                                       SecurityControlRepository securityControlRepository,
                                       OrgServiceRepository orgServiceRepository,
                                       OrgServiceService orgServiceService) {
        this.assessmentRepository = assessmentRepository;
        this.controlRepository = controlRepository;
        this.securityControlRepository = securityControlRepository;
        this.orgServiceRepository = orgServiceRepository;
        this.orgServiceService = orgServiceService;
    }

    public OrgServiceAssessment findOrCreateAssessment(Long orgServiceId) {
        List<OrgServiceAssessment> existing = assessmentRepository.findByOrgServiceId(orgServiceId);
        if (!existing.isEmpty()) return existing.get(0);

        OrgService orgService = orgServiceRepository.findById(orgServiceId).orElseThrow();
        OrgServiceAssessment assessment = new OrgServiceAssessment(orgService, LocalDate.now());
        List<SecurityControl> allControls = securityControlRepository.findAll();
        List<OrgServiceAssessment> allAssessments = assessmentRepository.findAll();

        List<OrgServiceAssessmentControl> controls = new ArrayList<>();
        for (SecurityControl sc : allControls) {
            OrgServiceAssessmentControl control = new OrgServiceAssessmentControl();
            control.setSecurityControl(sc);
            control.setApplicable(false);
            control.setPercent(0);
            control.setOrgServiceAssessment(assessment);
            controls.add(control);
        }
        assessment.setControls(controls);
        assessmentRepository.save(assessment);
        return assessment;
    }

    /**
     * Given an OrgServiceAssessment, returns a list of controls where each control
     * is marked if it is already answered by another service and set with that name.
     */
    public List<OrgServiceAssessmentControl> enrichControlsWithLockInfo(OrgServiceAssessment assessment) {
        List<OrgServiceAssessment> allAssessments = assessmentRepository.findAll();
        Map<Long, String> controlIdToServiceName = new HashMap<>();
        for (OrgServiceAssessment a : allAssessments) {
            if (assessment.getId() != null && a.getId() != null && assessment.getId().equals(a.getId())) continue;
            if (a.getOrgService() != null && a.getControls() != null) {
                for (OrgServiceAssessmentControl c : a.getControls()) {
                    if (c.isApplicable() && !controlIdToServiceName.containsKey(c.getSecurityControl().getId())) {
                        controlIdToServiceName.put(c.getSecurityControl().getId(), a.getOrgService().getName());
                    }
                }
            }
        }
        List<OrgServiceAssessmentControl> result = new ArrayList<>();
        for (OrgServiceAssessmentControl control : assessment.getControls()) {
            if (controlIdToServiceName.containsKey(control.getSecurityControl().getId())) {
                control.setAnsweredByAnotherAssessment(true);
                control.setAnsweredByOrgServiceName(controlIdToServiceName.get(control.getSecurityControl().getId()));
            } else {
                control.setAnsweredByAnotherAssessment(false);
                control.setAnsweredByOrgServiceName(null);
            }
            result.add(control);
        }
        return result;
    }

    public Optional<OrgServiceAssessment> getAssessment(Long assessmentId) {
        return assessmentRepository.findById(assessmentId);
    }

    @Transactional
    public void saveAssessment(OrgServiceAssessment assessment) {
        // Ensure each control correctly references its parent assessment
        // And ensure no duplicate maturity answer for a control per org service
        if (assessment.getOrgService() != null) {
            Long orgServiceId = assessment.getOrgService().getId();
            List<OrgServiceAssessment> allAssessments = assessmentRepository.findAll();
            for (OrgServiceAssessmentControl control : assessment.getControls()) {
                control.setOrgServiceAssessment(assessment);
                if (control.isApplicable()) {
                    // Check if any assessment *in any service* already answered this control
                    for (OrgServiceAssessment otherA : allAssessments) {
                        if (assessment.getId() != null && assessment.getId().equals(otherA.getId())) continue;
                        if (otherA.getControls() != null) {
                            for (OrgServiceAssessmentControl otherC : otherA.getControls()) {
                                if (otherC.getSecurityControl().getId().equals(control.getSecurityControl().getId()) && otherC.isApplicable()) {
                                    throw new RuntimeException("This control is already answered by another service: "
                                            + (otherA.getOrgService() != null ? otherA.getOrgService().getName() : "unknown") + ".");
                                }
                            }
                        }
                    }
                }
            }
        }
        assessmentRepository.save(assessment);
    }
}
