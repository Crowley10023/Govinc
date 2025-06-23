package com.govinc.organization;

import com.govinc.catalog.SecurityControl;
import com.govinc.catalog.SecurityControlRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class OrgServiceAssessmentService {
    private final OrgServiceAssessmentRepository assessmentRepository;
    private final OrgServiceAssessmentControlRepository controlRepository;
    private final SecurityControlRepository securityControlRepository;
    private final OrgServiceRepository orgServiceRepository;

    public OrgServiceAssessmentService(OrgServiceAssessmentRepository assessmentRepository,
                                       OrgServiceAssessmentControlRepository controlRepository,
                                       SecurityControlRepository securityControlRepository,
                                       OrgServiceRepository orgServiceRepository) {
        this.assessmentRepository = assessmentRepository;
        this.controlRepository = controlRepository;
        this.securityControlRepository = securityControlRepository;
        this.orgServiceRepository = orgServiceRepository;
    }

    public OrgServiceAssessment findOrCreateAssessment(Long orgServiceId) {
        List<OrgServiceAssessment> assessments = assessmentRepository.findByOrgServiceId(orgServiceId);
        if (!assessments.isEmpty()) {
            return assessments.get(0); // Only one assessment for now
        }
        OrgService orgService = orgServiceRepository.findById(orgServiceId).orElseThrow();
        OrgServiceAssessment assessment = new OrgServiceAssessment(orgService, LocalDate.now());
        List<OrgServiceAssessmentControl> controls = new ArrayList<>();
        List<SecurityControl> allControls = securityControlRepository.findAll();
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

    public Optional<OrgServiceAssessment> getAssessment(Long assessmentId) {
        return assessmentRepository.findById(assessmentId);
    }

    @Transactional
    public void saveAssessment(OrgServiceAssessment assessment) {
        // Ensure each control correctly references its parent assessment (fix for DataIntegrityViolationException)
        if (assessment.getControls() != null) {
            for (OrgServiceAssessmentControl control : assessment.getControls()) {
                control.setOrgServiceAssessment(assessment);
            }
        }
        assessmentRepository.save(assessment);
    }
}
