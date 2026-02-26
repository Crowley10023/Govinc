package com.govinc;

import com.govinc.assessment.Assessment;
import com.govinc.assessment.AssessmentRepository;
import com.govinc.authorization.AuthorizationService;
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

    @GetMapping("/")
    public String home(Model model) {
        try {
            // Fetch assessments and filter by access rights, then sort by date desc and take up to 10
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
            List<Assessment> latest = filtered.size() > 10 ? filtered.subList(0, 10) : filtered;
            model.addAttribute("latestAssessments", latest);
        } catch (Exception ex) {
            // Protect landing page from failing; show empty dashboard if something goes wrong
            model.addAttribute("latestAssessments", new ArrayList<Assessment>());
        }
        return "landing";
    }

    @GetMapping("/create-assessment")
    public String createAssessment() {
        return "create-assessment";
    }

}
