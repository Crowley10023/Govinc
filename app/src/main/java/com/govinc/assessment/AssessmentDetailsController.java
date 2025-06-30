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
        System.out.println(".... calling details!");
        Optional<AssessmentDetails> details = assessmentDetailsService.findById(id);
        if (details.isPresent()) {
            System.out.println(".... details present");
            AssessmentDetails ad = details.get();
            Map<String, Map<String, Object>> answerSummary = assessmentDetailsService.computeAnswerSummary(ad);

            // Try to get the first linked assessment
            Assessment assessment = null;
            if (ad.getAssessments() != null && !ad.getAssessments().isEmpty()) {
                assessment = ad.getAssessments().iterator().next();
            }
            System.out.println(".... now going on");
            // --- Improved logic for taken-over and display answer ---
            Map<Long, Boolean> controlAnswerIsTakenOver = new HashMap<>();
            Map<Long, String> controlTakenOverOrgServiceName = new HashMap<>();
            Map<Long, Long> orgServiceControlAnswers = new HashMap<>(); // control id -> maturityAnswerId
            List<MaturityAnswer> allMaturityAnswers = maturityAnswerRepository.findAll();
            if (assessment != null) {
                // For every SecurityControl in the catalog
                if (assessment.getSecurityCatalog() != null && assessment.getSecurityCatalog().getSecurityControls() != null) {
                    List<OrgService> assignedOrgServices = (assessment.getOrgServices() != null)
                        ? assessment.getOrgServices().stream().toList() : java.util.Collections.emptyList();
                    System.out.println("\n\n\n assessment: " + assessment.getName());
                    for (var ctrl : assessment.getSecurityCatalog().getSecurityControls()) {
                        boolean found = false;
                        String takenFromName = null;
                        int foundPercent = 0;
                        for (OrgService orgService : assignedOrgServices) {
                            System.out.println("   org service: " + orgService.getName());
                            OrgServiceAssessment orgServiceAssessment = orgServiceAssessmentService.findOrCreateAssessment(orgService.getId());
                            for (OrgServiceAssessmentControl orgServiceCtrl : orgServiceAssessment.getControls()) {
                                System.out.println( "      orgservice id: " + orgServiceCtrl.getSecurityControl().getId() + " --> ID control: " + ctrl.getId());
                                if (orgServiceCtrl.getSecurityControl().getId().equals(ctrl.getId())) {
                                    System.out.println("     ----- match!");
                                    if (orgServiceCtrl.getPercent() >= 0) {
                                        System.out.println("     ----- match done");
                                        found = true;
                                        takenFromName = orgService.getName();
                                        foundPercent = orgServiceCtrl.getPercent();
                                        break;
                                    }
                                }
                            }
                            if (found) break;
                        }
                        if (found) {
                            controlAnswerIsTakenOver.put(ctrl.getId(), true);
                            controlTakenOverOrgServiceName.put(ctrl.getId(), takenFromName);
                            // Match percent to maturity answer
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
                        }
                    }
                }
            }
            // Defensive: ensure every catalog control gets a value in controlAnswerIsTakenOver (never null)
            if (assessment != null && assessment.getSecurityCatalog() != null && assessment.getSecurityCatalog().getSecurityControls() != null) {
                for (var ctrl : assessment.getSecurityCatalog().getSecurityControls()) {
                    controlAnswerIsTakenOver.putIfAbsent(ctrl.getId(), false);
                }
            }
            // Build map for display: choose user answer if exists, else org service answer
            Map<Long, Long> controlDisplayAnswers = new HashMap<>();
            Set<com.govinc.assessment.AssessmentControlAnswer> detailAnswers = ad.getControlAnswers();
            if (assessment != null && detailAnswers != null) {
                for (var ctrl : assessment.getSecurityCatalog().getSecurityControls()) {
                    Long foundUserAnswerId = null;
                    for (com.govinc.assessment.AssessmentControlAnswer a : detailAnswers) {
                        if (a.getSecurityControl() != null && a.getSecurityControl().getId().equals(ctrl.getId()) && a.getMaturityAnswer() != null) {
                            foundUserAnswerId = a.getMaturityAnswer().getId();
                            break;
                        }
                    }
                    if (foundUserAnswerId != null) {
                        controlDisplayAnswers.put(ctrl.getId(), foundUserAnswerId);
                    } else if (orgServiceControlAnswers.containsKey(ctrl.getId())) {
                        controlDisplayAnswers.put(ctrl.getId(), orgServiceControlAnswers.get(ctrl.getId()));
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
            model.addAttribute("controlAnswerIsTakenOver", controlAnswerIsTakenOver);
            model.addAttribute("controlTakenOverOrgServiceName", controlTakenOverOrgServiceName);
            model.addAttribute("answerSummary", answerSummary);
            model.addAttribute("users", userRepository.findAll());
            model.addAttribute("orgUnits", orgUnitService.getAllOrgUnits());
            model.addAttribute("controlDisplayAnswers", controlDisplayAnswers);
            // DEBUG: Log to make sure we have org services:
            java.util.List<com.govinc.organization.OrgService> allOrgSvcs = orgServiceService.getAllOrgServices();
            System.out.println("OrgServicesAll for modal: size=" + allOrgSvcs.size() + " contents=" + allOrgSvcs);
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
        // Persist all maturity answers before setting them to AssessmentDetails
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

    // --- REST endpoint to provide org units for assessmentdetails.html ---
    @GetMapping("/orgunits")
    @ResponseBody
    public List<OrgUnit> getAllOrgUnitsForAssessment() {
        return orgUnitService.getAllOrgUnits();
    }
}
