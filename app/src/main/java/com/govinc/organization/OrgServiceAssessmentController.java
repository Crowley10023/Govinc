package com.govinc.organization;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Controller
@RequestMapping("/orgservice-assessment")
public class OrgServiceAssessmentController {
    private final OrgServiceAssessmentService assessmentService;

    @Autowired
    public OrgServiceAssessmentController(OrgServiceAssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @GetMapping("/edit/{orgServiceId}")
    public String editAssessment(@PathVariable Long orgServiceId, Model model) {
        OrgServiceAssessment assessment = assessmentService.findOrCreateAssessment(orgServiceId);
        long applicableCount = assessment.getControls().stream().filter(OrgServiceAssessmentControl::isApplicable).count();
        model.addAttribute("assessment", assessment);
        model.addAttribute("controls", assessment.getControls());
        model.addAttribute("applicableCount", applicableCount);
        return "orgservice-assessment";
    }

    @PostMapping("/save")
    public String saveAssessment(@ModelAttribute OrgServiceAssessment assessment, Model model) {
        if (assessment.getAssessmentDate() == null) {
            assessment.setAssessmentDate(java.time.LocalDate.now());
        }
        assessmentService.saveAssessment(assessment);
        // Redirect to orgservice edit view after save
        if (assessment.getOrgService() != null && assessment.getOrgService().getId() != null) {
            return "redirect:/orgservices/edit/" + assessment.getOrgService().getId();
        } else {
            return "redirect:/orgservices/list";
        }
    }
}
