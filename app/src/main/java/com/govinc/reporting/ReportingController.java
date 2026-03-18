package com.govinc.reporting;

import com.govinc.assessment.Assessment;
import com.govinc.assessment.AssessmentDetails;
import com.govinc.assessment.AssessmentDetailsService;
import com.govinc.assessment.AssessmentRepository;
import com.govinc.authorization.AuthorizationService;
import com.govinc.authorization.UnauthorizedException;
import com.govinc.organization.OrgUnit;
import com.govinc.organization.OrgUnitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/reporting")
public class ReportingController {

    @Autowired
    private OrgUnitService orgUnitService;

    @Autowired
    private AssessmentRepository assessmentRepository;

    @Autowired
    private AssessmentDetailsService assessmentDetailsService;

    @Autowired
    private AuthorizationService authorizationService;

    @GetMapping("/org-unit")
    public String orgUnitReportingPage(Model model) {
        if (!authorizationService.canAccessCompliance()) {
            throw new UnauthorizedException("You do not have permission to access reporting.");
        }
        List<OrgUnit> orgUnits = orgUnitService.getAllOrgUnits();
        orgUnits.sort(Comparator.comparing(OrgUnit::getName, String.CASE_INSENSITIVE_ORDER));
        model.addAttribute("orgUnits", orgUnits);
        return "org-unit-reporting";
    }

    @GetMapping("/org-unit/data")
    @ResponseBody
    public List<Map<String, Object>> getOrgUnitReportData(@RequestParam Long orgUnitId) {
        if (!authorizationService.canAccessCompliance()) {
            throw new UnauthorizedException("You do not have permission to access reporting.");
        }

        java.time.format.DateTimeFormatter labelFmt =
                java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy");

        // Collect all assessments for this org unit, sorted chronologically
        List<Assessment> assessments = assessmentRepository.findAll().stream()
                .filter(a -> a.getOrgUnit() != null && a.getOrgUnit().getId().equals(orgUnitId)
                        && a.getSecurityCatalog() != null)
                .sorted(Comparator.comparing(Assessment::getCreationDate,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        // Group assessments by catalog, preserving first-seen catalog order (alphabetical below)
        Map<Long, Map<String, Object>> catalogMap = new LinkedHashMap<>();

        for (Assessment a : assessments) {
            Long catalogId = a.getSecurityCatalog().getId();
            String catalogName = a.getSecurityCatalog().getName();
            int totalControls = a.getSecurityCatalog().getSecurityControls().size();

            Optional<AssessmentDetails> detailsOpt = assessmentDetailsService.findById(a.getId());
            if (!detailsOpt.isPresent()) continue;

            AssessmentDetails details = detailsOpt.get();
            ReportingCalculator.AssessmentStats stats =
                    ReportingCalculator.compute(details.getControlAnswers(), totalControls);

            String dateLabel = a.getCreationDate() != null
                    ? a.getCreationDate().format(labelFmt) : "Unknown";

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("assessmentId", a.getId());
            point.put("assessmentName", a.getName() != null ? a.getName() : "Assessment " + a.getId());
            point.put("dateLabel", dateLabel);
            point.put("averageRating", stats.averageRating);
            point.put("answeredCount", stats.answeredControls);
            point.put("totalControls", totalControls);
            point.put("coveragePercent", stats.coveragePercent);

            catalogMap.computeIfAbsent(catalogId, k -> {
                Map<String, Object> cat = new LinkedHashMap<>();
                cat.put("catalogId", catalogId);
                cat.put("catalogName", catalogName);
                cat.put("assessments", new ArrayList<Map<String, Object>>());
                return cat;
            });
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> pts =
                    (List<Map<String, Object>>) catalogMap.get(catalogId).get("assessments");
            pts.add(point);
        }

        List<Map<String, Object>> result = new ArrayList<>(catalogMap.values());
        result.sort(Comparator.comparing(m -> (String) m.get("catalogName")));
        return result;
    }
}
