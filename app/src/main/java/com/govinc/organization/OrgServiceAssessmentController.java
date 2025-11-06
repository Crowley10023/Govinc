package com.govinc.organization;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.List;

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
        List<OrgServiceAssessmentControl> controls = assessmentService.enrichControlsWithLockInfo(assessment);
        long applicableCount = controls.stream().filter(OrgServiceAssessmentControl::isApplicable).count();
        model.addAttribute("assessment", assessment);
        model.addAttribute("controls", controls);
        model.addAttribute("applicableCount", applicableCount);
        return "orgservice-assessment";
    }

    @PostMapping("/save")
    public String saveAssessment(@RequestParam Long id,
                                 @RequestParam Long orgServiceId,
                                 @RequestParam String assessmentDate,
                                 @RequestParam(required = false) String[] controlIds,
                                 @RequestParam(required = false) String[] applicable,
                                 @RequestParam(required = false) String[] percent,
                                 Model model) {
        try {
            OrgServiceAssessment assessment = assessmentService.getAssessment(id)
                    .orElseThrow(() -> new RuntimeException("Assessment not found"));
            
            // Update assessment date
            if (assessmentDate != null && !assessmentDate.isEmpty()) {
                assessment.setAssessmentDate(java.time.LocalDate.parse(assessmentDate));
            } else {
                assessment.setAssessmentDate(java.time.LocalDate.now());
            }
            
            // Update controls based on form submission
            List<OrgServiceAssessmentControl> controls = assessment.getControls();
            
            // Create a set of applicable control indices for quick lookup
            java.util.Set<String> applicableSet = new java.util.HashSet<>();
            if (applicable != null) {
                for (String app : applicable) {
                    applicableSet.add(app);
                }
            }
            
            // Create a map of percent values by control ID
            java.util.Map<String, String> percentMap = new java.util.HashMap<>();
            if (controlIds != null && percent != null) {
                for (int i = 0; i < Math.min(controlIds.length, percent.length); i++) {
                    percentMap.put(controlIds[i], percent[i]);
                }
            }
            
            // Update each control
            if (controlIds != null) {
                for (int i = 0; i < controlIds.length; i++) {
                    String controlId = controlIds[i];
                    OrgServiceAssessmentControl control = controls.get(i);
                    
                    // Set applicable based on whether control ID is in the applicable set
                    boolean isApplicable = applicableSet.contains(controlId);
                    control.setApplicable(isApplicable);
                    
                    // Set percent value
                    if (percentMap.containsKey(controlId)) {
                        try {
                            int percentValue = Integer.parseInt(percentMap.get(controlId));
                            control.setPercent(Math.min(100, Math.max(0, percentValue)));
                        } catch (NumberFormatException e) {
                            control.setPercent(0);
                        }
                    } else {
                        control.setPercent(0);
                    }
                }
            }
            
            assessmentService.saveAssessment(assessment);
            return "redirect:/orgservices/list";
        } catch (RuntimeException ex) {
            Long orgServiceIdLong = orgServiceId != null ? orgServiceId : null;
            OrgServiceAssessment fullAssessment = assessmentService.findOrCreateAssessment(orgServiceIdLong);
            List<OrgServiceAssessmentControl> controls = assessmentService.enrichControlsWithLockInfo(fullAssessment);
            long applicableCount = controls.stream().filter(OrgServiceAssessmentControl::isApplicable).count();
            model.addAttribute("assessment", fullAssessment);
            model.addAttribute("controls", controls);
            model.addAttribute("applicableCount", applicableCount);
            model.addAttribute("errorMsg", ex.getMessage());
            return "orgservice-assessment";
        }
    }
}
