package com.govinc;

import com.govinc.assessment.Assessment;
import com.govinc.assessment.AssessmentRepository;
import com.govinc.authorization.AuthorizationService;
import com.govinc.user.UserRepository;
import com.govinc.catalog.SecurityCatalogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Controller
public class LandingController {

    @Autowired
    private AssessmentRepository assessmentRepository;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SecurityCatalogRepository securityCatalogRepository;

    @GetMapping("/")
    public String home(Model model) {
        try {
            // Fetch assessments and filter by access rights, then sort by creationDate descending
            List<Assessment> all = assessmentRepository.findAll();
            List<Assessment> filtered = new ArrayList<>();
            for (Assessment a : all) {
                try {
                    if (a.getId() != null && authorizationService.canAccessAssessment(a.getId())) {
                        filtered.add(a);
                    }
                } catch (Exception e) {
                    // In case of any authorization or null id issues, skip this assessment
                }
            }
            // Sort by creationDate (new field) descending, with nulls last
            filtered.sort(Comparator.comparing(Assessment::getCreationDate, Comparator.nullsLast(Comparator.reverseOrder())));

            // Show all assessments that fit access rights and sorting criteria
            model.addAttribute("latestAssessments", filtered);

            // Populate users and catalogs for filter dropdowns
            model.addAttribute("filterUsers", userRepository.findAll());
            model.addAttribute("filterCatalogs", securityCatalogRepository.findAll());

        } catch (Exception ex) {
            // Protect landing page from failing; show empty dashboard if something goes wrong
            model.addAttribute("latestAssessments", new ArrayList<Assessment>());
            model.addAttribute("filterUsers", new ArrayList<>());
            model.addAttribute("filterCatalogs", new ArrayList<>());
        }
        return "landing";
    }

    @GetMapping("/create-assessment")
    public String createAssessment() {
        return "create-assessment";
    }

}
