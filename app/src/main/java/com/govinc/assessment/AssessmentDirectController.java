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

import com.govinc.maturity.MaturityAnswer;
import com.govinc.maturity.MaturityAnswerRepository;
import com.govinc.catalog.SecurityCatalog;
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
        System.out.println("show assessment 1 ;-) : " + obfuscatedId);
        Optional<AssessmentUrls> maybeUrl = assessmentUrlsService.findByObfuscated(obfuscatedId);
        if (maybeUrl.isPresent()) {
            AssessmentUrls urlEntity = maybeUrl.get();
            Assessment assessment = urlEntity.getAssessment();
            model.addAttribute("assessment", assessment);

            // Load assessment details for this assessment
            // If you want to filter by assessment, update this line accordingly
            List<AssessmentDetails> allDetails = detailsService.findAll();
            model.addAttribute("assessmentDetails", allDetails);

            // Load maturity answers that are valid for the assessment's security catalog
            SecurityCatalog catalog = assessment.getSecurityCatalog();
            Set<MaturityAnswer> validMaturityAnswers = null;
            if (catalog != null && catalog.getMaturityModel() != null) {
                MaturityModel maturityModel = catalog.getMaturityModel();
                validMaturityAnswers = maturityModel.getMaturityAnswers();
            }
            
            if (validMaturityAnswers == null) {
                // fallback: show no answers
                model.addAttribute("maturityAnswers", java.util.Collections.emptyList());
            } else {
                model.addAttribute("maturityAnswers", validMaturityAnswers);
            }

            // Defensive: always provide controlAnswers non-null
            model.addAttribute("controlAnswers", new java.util.HashMap<>());

            return "assessment-direct";
        } else {
            return "error";
        }
    }

    // Allow using /assessment-direct.html?id=...
    @GetMapping("/assessment-direct.html")
    public String showAssessmentDirectByParam(@RequestParam("id") String obfuscatedId, Model model) {
        System.out.println("show assessment 2 ;-) : " + obfuscatedId);
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
