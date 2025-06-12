package com.govinc.assessment;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.govinc.user.UserRepository; // <-- add import for UserRepository
import com.govinc.organization.OrgUnitService; // <-- add import

@Controller
@RequestMapping("/assessmentdetails")
public class AssessmentDetailsController {
    @Autowired
    private AssessmentDetailsService assessmentDetailsService;
    @Autowired
    private AssessmentControlAnswerRepository assessmentControlAnswerRepository;
    @Autowired
    private UserRepository userRepository; // <-- Inject UserRepository
    @Autowired
    private OrgUnitService orgUnitService; // <-- Inject OrgUnitService

    @GetMapping("/list")
    public String list(Model model) {
        model.addAttribute("assessment", assessmentDetailsService.findAll());
        return "assessmentdetails-list";
    }

    @GetMapping("/details/{id}")
    public String details(@PathVariable Long id, Model model) {
        Optional<AssessmentDetails> details = assessmentDetailsService.findById(id);
        if (details.isPresent()) {
            AssessmentDetails ad = details.get();
            Map<String, Map<String, Object>> answerSummary = assessmentDetailsService.computeAnswerSummary(ad);

            // Try to get the first linked assessment
            Assessment assessment = null;
            if (ad.getAssessments() != null && !ad.getAssessments().isEmpty()) {
                assessment = ad.getAssessments().iterator().next();
            }
            if (assessment != null) {
                model.addAttribute("assessment", assessment);
            } else {
                // Fallback: just show details as before
                model.addAttribute("assessment", ad);
            }
            model.addAttribute("answerSummary", answerSummary);
            model.addAttribute("users", userRepository.findAll()); // <-- Add all users to the model
            model.addAttribute("orgUnits", orgUnitService.getAllOrgUnits()); // Add org units for popup dropdown
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
}
