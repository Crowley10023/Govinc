package com.govinc.assessment;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.List;
import java.util.HashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.govinc.user.UserRepository;
import com.govinc.organization.OrgUnitService;
import com.govinc.organization.OrgUnit;
import com.govinc.organization.OrgServiceService;
import com.govinc.organization.OrgServiceAssessmentService;
import com.govinc.organization.OrgServiceAssessment;
import com.govinc.organization.OrgService;
import com.govinc.organization.OrgServiceAssessmentControl;
import com.govinc.assessment.Assessment;
import com.govinc.maturity.MaturityAnswer;
import com.govinc.maturity.MaturityAnswerRepository;

@Controller
@RequestMapping("/assessmentdetails")
public class AssessmentDetailsController {
    @Autowired
    private AssessmentDetailsService assessmentDetailsService;
    @Autowired
    private AssessmentControlAnswerRepository assessmentControlAnswerRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrgUnitService orgUnitService;
    @Autowired
    private OrgServiceService orgServiceService;
    @Autowired
    private OrgServiceAssessmentService orgServiceAssessmentService;
    @Autowired
    private MaturityAnswerRepository maturityAnswerRepository;

    @GetMapping("/list")
    public String list(Model model) {
        model.addAttribute("assessment", assessmentDetailsService.findAll());
        return "assessmentdetails-list";
    }

    @GetMapping("/details/{id}")
    public String details(@PathVariable Long id, Model model) {
        System.out.println("========= DETAILS CONTROLLER DEBUG =========");
        System.out.println("Loading AssessmentDetails for id=" + id);
        Optional<AssessmentDetails> details = assessmentDetailsService.findById(id);
        if (details.isPresent()) {
            System.out.println("Details present");
            AssessmentDetails ad = details.get();
            Map<String, Map<String, Object>> answerSummary = assessmentDetailsService.computeAnswerSummary(ad);

            // Try to get the first linked assessment
            Assessment assessment = null;
            if (ad.getAssessments() != null && !ad.getAssessments().isEmpty()) {
                assessment = ad.getAssessments().iterator().next();
            }
            System.out.println("Now proceeding with assessment logic");
            
            // --- Improved logic for taken-over and display answer ---
            Map<Long, Boolean> controlAnswerIsTakenOver = new HashMap<>();
            Map<Long, String> controlTakenOverOrgServiceName = new HashMap<>();
            Map<Long, Long> orgServiceControlAnswers = new HashMap<>();
            Map<Long, String> orgServiceControlComments = new HashMap<>();
            Map<Long, Boolean> controlAnswerIsOverridden = new HashMap<>();
            List<MaturityAnswer> allMaturityAnswers = maturityAnswerRepository.findAll();
            
            if (assessment != null) {
                if (assessment.getSecurityCatalog() != null && assessment.getSecurityCatalog().getSecurityControls() != null) {
                    List<OrgService> assignedOrgServices = (assessment.getOrgServices() != null)
                        ? assessment.getOrgServices().stream().toList() : java.util.Collections.emptyList();
                    System.out.println("Assessment: " + assessment.getName());
                    
                    for (var ctrl : assessment.getSecurityCatalog().getSecurityControls()) {
                        boolean found = false;
                        String takenFromName = null;
                        int foundPercent = 0;
                        String foundComment = null;
                        
                        for (OrgService orgService : assignedOrgServices) {
                            System.out.println("Checking org service: " + orgService.getName());
                            OrgServiceAssessment orgServiceAssessment = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                            
                            if (orgServiceAssessment.getControls() == null) {
                                continue;
                            }
                            
                            for (OrgServiceAssessmentControl orgServiceCtrl : orgServiceAssessment.getControls()) {
                                if (orgServiceCtrl.getSecurityControl() == null) {
                                    continue;
                                }
                                
                                if (orgServiceCtrl.getSecurityControl().getId().equals(ctrl.getId())) {
                                    if (orgServiceCtrl.getPercent() >= 0) {
                                        found = true;
                                        takenFromName = orgService.getName();
                                        foundPercent = orgServiceCtrl.getPercent();
                                        foundComment = orgServiceCtrl.getComment();
                                        System.out.println("Found org service answer for control " + ctrl.getId() + ", comment: [" + foundComment + "]");
                                        break;
                                    }
                                }
                            }
                            if (found) break;
                        }
                        
                        if (found) {
                            controlAnswerIsTakenOver.put(ctrl.getId(), true);
                            controlTakenOverOrgServiceName.put(ctrl.getId(), takenFromName);
                            controlAnswerIsOverridden.put(ctrl.getId(), false);
                            if (foundComment != null && !foundComment.isEmpty()) {
                                System.out.println("Storing comment for control " + ctrl.getId() + ": [" + foundComment + "]");
                                orgServiceControlComments.put(ctrl.getId(), foundComment);
                            }
                            
                            Long answerIdMatch = null;
                            for (MaturityAnswer ans : allMaturityAnswers) {
                                if (ans.getRating() == foundPercent) {
                                    answerIdMatch = ans.getId();
                                    break;
                                }
                            }
                            if (answerIdMatch != null) {
                                orgServiceControlAnswers.put(ctrl.getId(), answerIdMatch);
                            }
                        } else {
                            controlAnswerIsTakenOver.put(ctrl.getId(), false);
                            controlAnswerIsOverridden.put(ctrl.getId(), false);
                        }
                    }
                }
            }
            
            // Defensive: ensure every catalog control gets a value
            if (assessment != null && assessment.getSecurityCatalog() != null && assessment.getSecurityCatalog().getSecurityControls() != null) {
                for (var ctrl : assessment.getSecurityCatalog().getSecurityControls()) {
                    controlAnswerIsTakenOver.putIfAbsent(ctrl.getId(), false);
                    controlAnswerIsOverridden.putIfAbsent(ctrl.getId(), false);
                }
            }
            
            // Build map for display: choose user answer if exists, else org service answer
            Map<Long, Long> controlDisplayAnswers = new HashMap<>();
            Map<Long, String> controlComments = new HashMap<>();
            Set<com.govinc.assessment.AssessmentControlAnswer> detailAnswers = ad.getControlAnswers();
            
            if (assessment != null && detailAnswers != null) {
                for (var ctrl : assessment.getSecurityCatalog().getSecurityControls()) {
                    Long foundUserAnswerId = null;
                    String foundUserComment = null;
                    Boolean foundUserOverride = false;
                    
                    for (com.govinc.assessment.AssessmentControlAnswer a : detailAnswers) {
                        if (a.getSecurityControl() != null && a.getSecurityControl().getId().equals(ctrl.getId()) && a.getMaturityAnswer() != null) {
                            foundUserAnswerId = a.getMaturityAnswer().getId();
                            foundUserComment = a.getComment();
                            foundUserOverride = a.getIsOverride() != null ? a.getIsOverride() : false;
                            break;
                        }
                    }
                    
                    if (foundUserAnswerId != null) {
                        controlDisplayAnswers.put(ctrl.getId(), foundUserAnswerId);
                        // If this is an override of a taken-over answer, mark it as overridden
                        if (foundUserOverride && controlAnswerIsTakenOver.get(ctrl.getId())) {
                            controlAnswerIsOverridden.put(ctrl.getId(), true);
                            // Keep the org service name visible even when overridden
                            // (controlTakenOverOrgServiceName is already populated from first loop)
                        }
                        if (foundUserComment != null && !foundUserComment.isEmpty()) {
                            controlComments.put(ctrl.getId(), foundUserComment);
                        } else if (orgServiceControlComments.containsKey(ctrl.getId())) {
                            controlComments.put(ctrl.getId(), orgServiceControlComments.get(ctrl.getId()));
                        }
                    } else if (orgServiceControlAnswers.containsKey(ctrl.getId())) {
                        controlDisplayAnswers.put(ctrl.getId(), orgServiceControlAnswers.get(ctrl.getId()));
                        if (orgServiceControlComments.containsKey(ctrl.getId())) {
                            controlComments.put(ctrl.getId(), orgServiceControlComments.get(ctrl.getId()));
                        }
                    }
                }
                
                for (com.govinc.assessment.AssessmentControlAnswer a : detailAnswers) {
                    if (a.getSecurityControl() != null && a.getComment() != null && !a.getComment().isEmpty()) {
                        controlComments.put(a.getSecurityControl().getId(), a.getComment());
                    }
                }
            }

            if (assessment != null) {
                model.addAttribute("assessment", assessment);
                List<Long> selectedOrgServiceIds = (assessment.getOrgServices() != null)
                        ? assessment.getOrgServices().stream().map(orgService -> orgService.getId())
                            .collect(java.util.stream.Collectors.toList())
                        : java.util.Collections.emptyList();
                model.addAttribute("selectedOrgServiceIds", selectedOrgServiceIds);
            } else {
                model.addAttribute("assessment", ad);
                model.addAttribute("selectedOrgServiceIds", java.util.Collections.emptyList());
            }
            
            System.out.println("Final controlComments size: " + controlComments.size());
            
            model.addAttribute("controlAnswerIsTakenOver", controlAnswerIsTakenOver);
            model.addAttribute("controlAnswerIsOverridden", controlAnswerIsOverridden);
            model.addAttribute("controlTakenOverOrgServiceName", controlTakenOverOrgServiceName);
            model.addAttribute("answerSummary", answerSummary);
            model.addAttribute("users", userRepository.findAll());
            model.addAttribute("orgUnits", orgUnitService.getAllOrgUnits());
            model.addAttribute("controlDisplayAnswers", controlDisplayAnswers);
            model.addAttribute("controlComments", controlComments);
            model.addAttribute("orgServiceControlComments", orgServiceControlComments);
            
            java.util.List<com.govinc.organization.OrgService> allOrgSvcs = orgServiceService.getAllOrgServices();
            System.out.println("OrgServicesAll for modal: size=" + allOrgSvcs.size());
            model.addAttribute("orgServicesAll", allOrgSvcs);
            
            return "assessment-details";
        } else {
            return "redirect:/assessmentdetails/list";
        }
    }

    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Long id, Model model) {
        Optional<AssessmentDetails> details = assessmentDetailsService.findById(id);
        if (details.isPresent()) {
            model.addAttribute("assessment", details.get());
            return "assessmentdetails-edit";
        } else {
            return "redirect:/assessmentdetails/list";
        }
    }

    @PostMapping("/save")
    public String save(@ModelAttribute AssessmentDetails details) {
        if (details.getControlAnswers() != null) {
            Set<AssessmentControlAnswer> savedAnswers = new java.util.HashSet<>();
            for (AssessmentControlAnswer answer : details.getControlAnswers()) {
                savedAnswers.add(assessmentControlAnswerRepository.save(answer));
            }
            details.setControlAnswers(savedAnswers);
        }
        assessmentDetailsService.save(details);
        return "redirect:/assessmentdetails/list";
    }

    @GetMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        assessmentDetailsService.deleteById(id);
        return "redirect:/assessmentdetails/list";
    }

    @GetMapping("/orgunits")
    @ResponseBody
    public List<OrgUnit> getAllOrgUnitsForAssessment() {
        return orgUnitService.getAllOrgUnits();
    }
}
