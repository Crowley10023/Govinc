package com.govinc.organization;

import com.govinc.authorization.AuthorizationService;
import com.govinc.authorization.UnauthorizedException;
import com.govinc.user.User;
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
    private AuthorizationService authorizationService;

    @Autowired
    private OrgServiceRepository orgServiceRepository;

    @Autowired
    public OrgServiceAssessmentController(OrgServiceAssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    /** Returns true if the current user is a responsible person on this org service. */
    private boolean isResponsiblePerson(Long orgServiceId) {
        User currentUser = authorizationService.getCurrentUser();
        if (currentUser == null) return false;
        return orgServiceRepository.findById(orgServiceId)
                .map(svc -> svc.getResponsiblePersons() != null &&
                        svc.getResponsiblePersons().stream().anyMatch(u -> u.getId().equals(currentUser.getId())))
                .orElse(false);
    }

    private boolean canAccessAssessmentFor(Long orgServiceId) {
        return authorizationService.canAccessOrganization() || isResponsiblePerson(orgServiceId);
    }

    @Autowired
    private OrgServiceAssessmentRepository orgServiceAssessmentRepository;

    @GetMapping("/list")
    public String listAssessments(Model model) {
        if (!authorizationService.canAccessOrganization()) {
            throw new UnauthorizedException("You do not have permission to view org assessments.");
        }
        List<OrgServiceAssessment> assessments = orgServiceAssessmentRepository.findAll();
        model.addAttribute("assessments", assessments);
        return "orgservice-assessment-list";
    }

    @GetMapping("/edit/{orgServiceId}")
    public String editAssessment(@PathVariable Long orgServiceId, Model model) {
        if (!authorizationService.canAccessOrganization()) {
            throw new UnauthorizedException("You do not have permission to access org service assessments.");
        }
        OrgServiceAssessment assessment = assessmentService.findOrCreateAssessment(orgServiceId);
        List<OrgServiceAssessmentControl> controls = assessmentService.getAllControlsForAssessment(assessment);
        long applicableCount = controls.stream().filter(OrgServiceAssessmentControl::isApplicable).count();
        model.addAttribute("assessment", assessment);
        model.addAttribute("controls", controls);
        model.addAttribute("applicableCount", applicableCount);
        return "orgservice-assessment";
    }

    @GetMapping("/simple/{orgServiceId}")
    public String simpleView(@PathVariable Long orgServiceId, Model model) {
        if (!canAccessAssessmentFor(orgServiceId)) {
            throw new UnauthorizedException("You do not have permission to access this org service assessment.");
        }
        OrgServiceAssessment assessment = assessmentService.findOrCreateAssessment(orgServiceId);
        List<OrgServiceAssessmentControl> allControls = assessmentService.getAllControlsForAssessment(assessment);
        List<OrgServiceAssessmentControl> applicableControls = allControls.stream()
                .filter(OrgServiceAssessmentControl::isApplicable)
                .collect(java.util.stream.Collectors.toList());
        model.addAttribute("assessment", assessment);
        model.addAttribute("controls", applicableControls);
        return "orgservice-assessment-simple";
    }

    @PostMapping("/save-control")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveControl(@RequestParam Long id,
                                                            @RequestParam Long orgServiceId,
                                                            @RequestParam String assessmentDate,
                                                            @RequestParam Long controlId,
                                                            @RequestParam Boolean applicable,
                                                            @RequestParam Integer percent) {
        if (!canAccessAssessmentFor(orgServiceId)) {
            Map<String, Object> forbidden = new HashMap<>();
            forbidden.put("success", false);
            forbidden.put("message", "You do not have permission to modify org service assessments.");
            return ResponseEntity.status(403).body(forbidden);
        }
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

    @PutMapping("/save-control-comment")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveControlComment(@RequestBody Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();
        try {
            Long id = Long.parseLong(body.get("id").toString());
            Long controlId = Long.parseLong(body.get("controlId").toString());
            String comment = (String) body.get("comment");
            
            OrgServiceAssessment assessment = assessmentService.getAssessment(id)
                    .orElseThrow(() -> new RuntimeException("Assessment not found"));

            Long svcId = assessment.getOrgService().getId();
            if (!canAccessAssessmentFor(svcId)) {
                response.put("success", false);
                response.put("message", "You do not have permission to modify org service assessments.");
                return ResponseEntity.status(403).body(response);
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
            
            // Update comment
            controlToUpdate.setComment(comment != null ? comment : "");
            
            assessmentService.saveAssessment(assessment);
            
            response.put("success", true);
            response.put("message", "Comment saved successfully");
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            response.put("success", false);
            response.put("message", ex.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
