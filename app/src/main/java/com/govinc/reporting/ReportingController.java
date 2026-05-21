package com.govinc.reporting;

import com.govinc.assessment.Assessment;
import com.govinc.assessment.AssessmentControlAnswer;
import com.govinc.assessment.AssessmentDetails;
import com.govinc.assessment.AssessmentDetailsService;
import com.govinc.assessment.AssessmentRepository;
import com.govinc.authorization.AuthorizationService;
import com.govinc.authorization.UnauthorizedException;
import com.govinc.catalog.SecurityCatalog;
import com.govinc.catalog.SecurityCatalogService;
import com.govinc.catalog.SecurityControl;
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

    @Autowired
    private SecurityCatalogService securityCatalogService;

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
            // Use effective controls to respect snapshot for closed assessments
            int totalControls = a.getEffectiveControls().size();

            Optional<AssessmentDetails> detailsOpt = assessmentDetailsService.findByAssessmentId(a.getId());
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

    @GetMapping("/compare")
    public String compareAssessmentsPage(Model model) {
        if (!authorizationService.canAccessCompliance()) {
            throw new UnauthorizedException("You do not have permission to access reporting.");
        }
        List<SecurityCatalog> catalogs = securityCatalogService.findAll();
        catalogs.sort(Comparator.comparing(SecurityCatalog::getName, String.CASE_INSENSITIVE_ORDER));
        model.addAttribute("catalogs", catalogs);
        return "assessment-compare";
    }

    @GetMapping("/compare/assessments")
    @ResponseBody
    public List<Map<String, Object>> getAssessmentsForCompare(
            @RequestParam(required = false) Long catalogId,
            @RequestParam(required = false) Long orgUnitId) {
        if (!authorizationService.canAccessCompliance()) {
            throw new UnauthorizedException("You do not have permission to access reporting.");
        }
        List<Assessment> assessments;
        if (catalogId != null && orgUnitId != null) {
            assessments = assessmentRepository.findBySecurityCatalogIdAndOrgUnitId(catalogId, orgUnitId);
        } else if (catalogId != null) {
            final Long cid = catalogId;
            assessments = assessmentRepository.findAll().stream()
                    .filter(a -> a.getSecurityCatalog() != null && cid.equals(a.getSecurityCatalog().getId()))
                    .collect(Collectors.toList());
        } else {
            assessments = assessmentRepository.findAll();
        }
        assessments.sort(Comparator.comparing(Assessment::getCreationDate,
                Comparator.nullsFirst(Comparator.naturalOrder())));
        java.time.format.DateTimeFormatter fmt =
                java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Assessment a : assessments) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", a.getId());
            item.put("name", a.getName());
            item.put("status", a.getStatus().toString());
            item.put("creationDate", a.getCreationDate() != null ? a.getCreationDate().format(fmt) : "");
            item.put("orgUnitName", a.getOrgUnit() != null ? a.getOrgUnit().getName() : "");
            result.add(item);
        }
        return result;
    }

    @GetMapping("/compare/data")
    @ResponseBody
    public Map<String, Object> getCompareData(
            @RequestParam Long a1,
            @RequestParam Long a2) {
        if (!authorizationService.canAccessCompliance()) {
            throw new UnauthorizedException("You do not have permission to access reporting.");
        }
        java.time.format.DateTimeFormatter fmt =
                java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy");
        Map<String, Object> result = new LinkedHashMap<>();
        Assessment assessment1 = assessmentRepository.findById(a1).orElse(null);
        Assessment assessment2 = assessmentRepository.findById(a2).orElse(null);
        if (assessment1 == null || assessment2 == null) {
            result.put("error", "One or both assessments not found.");
            return result;
        }
        Optional<AssessmentDetails> details1Opt = assessmentDetailsService.findByAssessmentId(a1);
        Optional<AssessmentDetails> details2Opt = assessmentDetailsService.findByAssessmentId(a2);
        Map<Long, AssessmentControlAnswer> answers1 = new HashMap<>();
        Map<Long, AssessmentControlAnswer> answers2 = new HashMap<>();
        if (details1Opt.isPresent() && details1Opt.get().getControlAnswers() != null) {
            for (AssessmentControlAnswer aca : details1Opt.get().getControlAnswers()) {
                if (aca.getSecurityControl() != null) answers1.put(aca.getSecurityControl().getId(), aca);
            }
        }
        if (details2Opt.isPresent() && details2Opt.get().getControlAnswers() != null) {
            for (AssessmentControlAnswer aca : details2Opt.get().getControlAnswers()) {
                if (aca.getSecurityControl() != null) answers2.put(aca.getSecurityControl().getId(), aca);
            }
        }
        // Union of both assessments' effective controls
        Map<Long, SecurityControl> allControls = new LinkedHashMap<>();
        for (SecurityControl sc : assessment1.getEffectiveControls()) allControls.put(sc.getId(), sc);
        for (SecurityControl sc : assessment2.getEffectiveControls()) allControls.put(sc.getId(), sc);
        List<SecurityControl> sortedControls = new ArrayList<>(allControls.values());
        sortedControls.sort(Comparator
                .comparing(SecurityControl::getReference, Comparator.nullsLast(String::compareTo))
                .thenComparing(SecurityControl::getName, Comparator.nullsLast(String::compareTo)));
        result.put("assessment1", buildAssessmentMeta(assessment1, fmt));
        result.put("assessment2", buildAssessmentMeta(assessment2, fmt));
        List<Map<String, Object>> controls = new ArrayList<>();
        for (SecurityControl sc : sortedControls) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("controlId", sc.getId());
            row.put("controlName", sc.getName());
            row.put("controlReference", sc.getReference());
            row.put("domainName", sc.getSecurityControlDomain() != null ? sc.getSecurityControlDomain().getName() : "");
            AssessmentControlAnswer aca1 = answers1.get(sc.getId());
            AssessmentControlAnswer aca2 = answers2.get(sc.getId());
            String answer1Text = aca1 != null && aca1.getMaturityAnswer() != null ? aca1.getMaturityAnswer().getAnswer() : null;
            int rating1 = aca1 != null && aca1.getMaturityAnswer() != null ? aca1.getMaturityAnswer().getRating() : -1;
            String answer2Text = aca2 != null && aca2.getMaturityAnswer() != null ? aca2.getMaturityAnswer().getAnswer() : null;
            int rating2 = aca2 != null && aca2.getMaturityAnswer() != null ? aca2.getMaturityAnswer().getRating() : -1;
            row.put("answer1", answer1Text);
            row.put("rating1", rating1);
            row.put("answer2", answer2Text);
            row.put("rating2", rating2);
            row.put("same", Objects.equals(answer1Text, answer2Text));
            controls.add(row);
        }
        result.put("controls", controls);
        return result;
    }

    private Map<String, Object> buildAssessmentMeta(Assessment a,
            java.time.format.DateTimeFormatter fmt) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("id", a.getId());
        meta.put("name", a.getName());
        meta.put("status", a.getStatus().toString());
        meta.put("creationDate", a.getCreationDate() != null ? a.getCreationDate().format(fmt) : "");
        meta.put("closeDate", a.getCloseDate() != null ? a.getCloseDate().format(fmt) : "");
        meta.put("catalogName", a.getSecurityCatalog() != null ? a.getSecurityCatalog().getName() : "");
        meta.put("orgUnitName", a.getOrgUnit() != null ? a.getOrgUnit().getName() : "");
        return meta;
    }
}
