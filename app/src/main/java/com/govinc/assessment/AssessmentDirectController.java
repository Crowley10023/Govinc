package com.govinc.assessment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;
import java.util.Objects;

import com.govinc.maturity.MaturityAnswer;
import com.govinc.maturity.MaturityAnswerRepository;
import com.govinc.assessment.Assessment;
import com.govinc.catalog.SecurityCatalog;
import com.govinc.catalog.SecurityControl;
import com.govinc.catalog.SecurityControlDomain;
import com.govinc.maturity.MaturityModel;

@Controller
public class AssessmentDirectController {
    @Autowired
    private AssessmentUrlsService assessmentUrlsService;
    @Autowired
    private AssessmentDetailsService detailsService;
    @Autowired
    private MaturityAnswerRepository maturityAnswerRepository; // Added for maturity answers

    @GetMapping("/assessment-direct/{obfuscatedId}")
    public String showAssessmentDirect(@PathVariable String obfuscatedId, Model model) {
        Optional<AssessmentUrls> maybeUrl = assessmentUrlsService.findByObfuscated(obfuscatedId);
        if (maybeUrl.isPresent()) {
            AssessmentUrls urlEntity = maybeUrl.get();
            com.govinc.assessment.Assessment assessment = urlEntity.getAssessment();
            System.out.println("\n\nassessment: " + assessment.getName());

            model.addAttribute("assessment", assessment);

            // Control answers are always retrieved from AssessmentDetails
            Optional<AssessmentDetails> detailsOpt = detailsService.findById(assessment.getId());
            AssessmentDetails details = detailsOpt.orElse(null);
            List<AssessmentControlAnswer> answers = new ArrayList<>();
            Map<Long, String> controlAnswers = new HashMap<>();
            if (details != null && details.getControlAnswers() != null) {
                answers.addAll(details.getControlAnswers());
                for (AssessmentControlAnswer aca : details.getControlAnswers()) {
                    if (aca.getSecurityControl() != null && aca.getMaturityAnswer() != null)
                        controlAnswers.put(aca.getSecurityControl().getId(), aca.getMaturityAnswer().getAnswer());
                }
            }
            model.addAttribute("answers", answers);
            model.addAttribute("controlAnswers", controlAnswers);

            // Summary table by answer type
            model.addAttribute("answerSummary", detailsService.computeAnswerSummary(details));

            // Use only controls from the catalog assigned to this assessment
            // Sorted controls by name
            List<SecurityControl> controls = new ArrayList<>();
            if (assessment.getSecurityCatalog() != null) {
                controls.addAll(assessment.getSecurityCatalog().getSecurityControls());
                controls.sort(Comparator.comparing(SecurityControl::getName, Comparator.nullsLast(String::compareTo)));
            }
            model.addAttribute("controls", controls);

            // Add securityControlDomains to model (prevents null in Thymeleaf)
            List<SecurityControlDomain> securityControlDomains = controls.stream()
                .map(SecurityControl::getSecurityControlDomain)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(SecurityControlDomain::getName, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
            model.addAttribute("securityControlDomains", securityControlDomains);

            // Pass the correct answers from the associated maturity model only
            // Sorted maturity answers
            List<MaturityAnswer> maturityAnswers = new ArrayList<>();
            if (assessment.getSecurityCatalog() != null && assessment.getSecurityCatalog().getMaturityModel() != null) {
                maturityAnswers.addAll(assessment.getSecurityCatalog().getMaturityModel().getMaturityAnswers());
                maturityAnswers.sort(Comparator.comparing(MaturityAnswer::getAnswer, Comparator.nullsLast(String::compareTo)));
            }
            model.addAttribute("maturityAnswers", maturityAnswers);

            return "assessment-direct";
        } else {
            return "error";
        }
    }

    // Allow using /assessment-direct.html?id=...
    @GetMapping("/assessment-direct.html")
    public String showAssessmentDirectByParam(@RequestParam("id") String obfuscatedId, Model model) {
        return showAssessmentDirect(obfuscatedId, model);
    }

    // Web page listing all Assessment URLs
    @GetMapping("/assessment-urls-list")
    public String showAllAssessmentUrls(Model model) {
        System.out.println("show assessment 3 ;-) : " + model);
        List<AssessmentUrls> allUrls = assessmentUrlsService.findAll();
        model.addAttribute("urls", allUrls);
        return "assessment-urls-list";
    }
}
