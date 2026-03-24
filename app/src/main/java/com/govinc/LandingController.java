package com.govinc;

import com.govinc.assessment.Assessment;
import com.govinc.assessment.AssessmentRepository;
import com.govinc.authorization.AuthorizationService;
import com.govinc.user.UserRepository;
import com.govinc.catalog.SecurityCatalogRepository;
import com.govinc.assessment.AssessmentDetailsService;
import com.govinc.assessment.AssessmentDetails;
import com.govinc.organization.OrgServiceAssessment;
import com.govinc.organization.OrgServiceAssessmentControl;
import com.govinc.organization.OrgServiceAssessmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @Autowired
    private AssessmentDetailsService assessmentDetailsService;

    @Autowired
    private OrgServiceAssessmentRepository orgServiceAssessmentRepository;

    @GetMapping("/")
    public String home(Model model) {
        try {
            // Fetch assessments and filter by access rights, then sort by creationDate descending
            List<Assessment> all = assessmentRepository.findAll();
            List<Assessment> filtered = new ArrayList<>();
            for (Assessment a : all) {
                try {
                    // Pass the Assessment object directly — avoids a redundant findById per item.
                    // canAccessAssessment already includes leadership checks internally.
                    if (a.getId() != null && authorizationService.canAccessAssessment(a)) {
                        filtered.add(a);
                    }
                } catch (Exception e) {
                    // In case of any authorization or null id issues, skip this assessment
                }
            }
            // Sort by creationDate (new field) descending, with nulls last
            filtered.sort(Comparator.comparing(Assessment::getCreationDate, Comparator.nullsLast(Comparator.reverseOrder())));

            // --- Batch pre-loads to avoid N+1 queries ---
            // 1. Load all AssessmentDetails for filtered assessments in one query
            List<Long> filteredIds = new ArrayList<>();
            for (Assessment a : filtered) filteredIds.add(a.getId());
            Map<Long, AssessmentDetails> detailsById = assessmentDetailsService.findAllByAssessmentIds(filteredIds);

            // 2. Collect all org service IDs across filtered assessments, load in one query
            List<Long> allOrgServiceIds = new ArrayList<>();
            for (Assessment a : filtered) {
                if (a.getOrgServices() != null) {
                    for (com.govinc.organization.OrgService os : a.getOrgServices()) {
                        allOrgServiceIds.add(os.getId());
                    }
                }
            }
            Map<Long, List<OrgServiceAssessment>> osaByOrgServiceId = new HashMap<>();
            if (!allOrgServiceIds.isEmpty()) {
                List<OrgServiceAssessment> allOsa = orgServiceAssessmentRepository.findByOrgServiceIdIn(allOrgServiceIds);
                for (OrgServiceAssessment osa : allOsa) {
                    if (osa.getOrgService() != null && osa.getOrgService().getId() != null) {
                        osaByOrgServiceId.computeIfAbsent(osa.getOrgService().getId(), k -> new ArrayList<>()).add(osa);
                    }
                }
            }

            // Prepare maps for completion and team leader display
            Map<Long, Integer> assessmentTotalControls = new HashMap<>();
            Map<Long, Integer> assessmentAnsweredControls = new HashMap<>();
            Map<Long, Integer> assessmentCompletionPercent = new HashMap<>();
            Map<Long, String> assessmentTeamLeaders = new HashMap<>();

            for (Assessment a : filtered) {
                Long aid = a.getId();
                int totalControls = 0;
                if (a.getSecurityCatalog() != null && a.getSecurityCatalog().getSecurityControls() != null) {
                    totalControls = a.getSecurityCatalog().getSecurityControls().size();
                }
                int answered = 0;
                try {
                    // Collect directly-answered control IDs from pre-loaded assessment details
                    Set<Long> answeredIds = new HashSet<>();
                    AssessmentDetails details = detailsById.get(aid);
                    if (details != null && details.getControlAnswers() != null) {
                        for (com.govinc.assessment.AssessmentControlAnswer aca : details.getControlAnswers()) {
                            if (aca != null && aca.getSecurityControl() != null && aca.getSecurityControl().getId() != null) {
                                answeredIds.add(aca.getSecurityControl().getId());
                            }
                        }
                    }
                    // Also count controls taken over by org services — use pre-loaded map
                    if (a.getOrgServices() != null && !a.getOrgServices().isEmpty()) {
                        for (com.govinc.organization.OrgService orgService : a.getOrgServices()) {
                            List<OrgServiceAssessment> osaList = osaByOrgServiceId.get(orgService.getId());
                            if (osaList != null) {
                                for (OrgServiceAssessment osa : osaList) {
                                    if (osa.getControls() != null) {
                                        for (OrgServiceAssessmentControl osac : osa.getControls()) {
                                            if (osac.isApplicable() && osac.getSecurityControl() != null
                                                    && osac.getSecurityControl().getId() != null) {
                                                answeredIds.add(osac.getSecurityControl().getId());
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    answered = answeredIds.size();
                } catch (Exception e) {
                    // swallow any errors retrieving details
                }
                int percent = 0;
                if (totalControls > 0) {
                    percent = Math.round((answered * 100.0f) / totalControls);
                    if (percent < 0) percent = 0;
                    if (percent > 100) percent = 100;
                }
                assessmentTotalControls.put(aid, totalControls);
                assessmentAnsweredControls.put(aid, answered);
                assessmentCompletionPercent.put(aid, percent);

                String leaderName = "";
                try {
                    if (a.getOrgUnit() != null && a.getOrgUnit().getLeader() != null) {
                        leaderName = a.getOrgUnit().getLeader().getName();
                    }
                } catch (Exception e) {
                    leaderName = "";
                }
                assessmentTeamLeaders.put(aid, leaderName);
            }

            // Show all assessments that fit access rights and sorting criteria
            model.addAttribute("latestAssessments", filtered);

            // Add computed maps to model
            model.addAttribute("assessmentTotalControls", assessmentTotalControls);
            model.addAttribute("assessmentAnsweredControls", assessmentAnsweredControls);
            model.addAttribute("assessmentCompletionPercent", assessmentCompletionPercent);
            model.addAttribute("assessmentTeamLeaders", assessmentTeamLeaders);

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
