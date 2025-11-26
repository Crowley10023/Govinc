package com.govinc.organization;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @PostMapping("/save-control")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveControl(@RequestParam Long id,
                                                            @RequestParam Long orgServiceId,
                                                            @RequestParam String assessmentDate,
                                                            @RequestParam Long controlId,
                                                            @RequestParam Boolean applicable,
                                                            @RequestParam Integer percent) {
        Map<String, Object> response = new HashMap<>();
        try {
            OrgServiceAssessment assessment = assessmentService.getAssessment(id)
                    .orElseThrow(() -> new RuntimeException("Assessment not found"));
            
            // Update assessment date if needed
            if (assessmentDate != null && !assessmentDate.isEmpty()) {
                assessment.setAssessmentDate(java.time.LocalDate.parse(assessmentDate));
            }
            
            // Find and update the specific control
            List<OrgServiceAssessmentControl> controls = assessment.getControls();
            OrgServiceAssessmentControl controlToUpdate = controls.stream()
                    .filter(c -> c.getSecurityControl().getId().equals(controlId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Control not found"));
            
            // Check if the control is locked (answered by another assessment)
            if (controlToUpdate.isAnsweredByAnotherAssessment()) {
                response.put("success", false);
                response.put("message", "This control is locked by another assessment");
                return ResponseEntity.status(400).body(response);
            }
            
            // Update control values
            controlToUpdate.setApplicable(applicable);
            controlToUpdate.setPercent(Math.min(100, Math.max(0, percent)));
            
            assessmentService.saveAssessment(assessment);
            
            response.put("success", true);
            response.put("message", "Control saved successfully");
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            response.put("success", false);
            response.put("message", ex.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
