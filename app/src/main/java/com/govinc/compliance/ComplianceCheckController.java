package com.govinc.compliance;

import com.govinc.organization.OrgUnit;
import com.govinc.organization.OrgUnitService;
import com.govinc.catalog.SecurityCatalog;
import com.govinc.catalog.SecurityCatalogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.*;

@Controller
@RequestMapping("/compliance")
public class ComplianceCheckController {
    @Autowired
    private ComplianceService complianceService;
    @Autowired
    private ComplianceCheckRepository complianceCheckRepository;
    @Autowired
    private ComplianceThresholdRepository thresholdRepository;
    @Autowired
    private OrgUnitService orgUnitService;
    @Autowired
    private SecurityCatalogService catalogService;

    // === View compliance results ===
    @GetMapping("/view")
    public String showView(@RequestParam(required = false) Long orgUnitId,
                           @RequestParam(required = false) Long checkId,
                           Model model) {
        List<OrgUnit> orgUnits = orgUnitService.getAllOrgUnits();
        List<ComplianceCheck> checks = complianceService.findAll();
        ComplianceService.ComplianceResult result = null;
        OrgUnit selectedOrg = null;
        ComplianceCheck selectedCheck = null;
        Map<OrgUnit, ComplianceService.ComplianceResult> resultsForChildren = null;
        if (orgUnitId != null && checkId != null) {
            selectedOrg = orgUnitService.getOrgUnit(orgUnitId).orElse(null);
            selectedCheck = complianceService.findById(checkId).orElse(null);
            if (selectedOrg != null && selectedCheck != null) {
                // Get single result for the selected OrgUnit only (in case user wants summary for the head unit)
                result = complianceService.calculateCompliance(selectedCheck, selectedOrg);
                // All children and parent coverage & compliance
                SecurityCatalog catalog = selectedCheck.getSecurityCatalog();
                resultsForChildren = complianceService.evaluateComplianceForOrgAndChildren(selectedOrg, selectedCheck, catalog);
                // Bulletproof: ensure map!
                if (result != null && !(result.getThresholdsDetails() instanceof Map)) {
                    result = new ComplianceService.ComplianceResult(result.isCompliant(), new HashMap<>(), result.getCheckedAssessments());
                }
            }
        }
        model.addAttribute("orgUnits", orgUnits);
        model.addAttribute("checks", checks);
        model.addAttribute("selectedOrg", selectedOrg);
        model.addAttribute("selectedCheck", selectedCheck);
        model.addAttribute("result", result);
        model.addAttribute("resultsForChildren", resultsForChildren);
        if (resultsForChildren != null) {
            model.addAttribute("totalCoveragePercent", complianceService.getLatestTotalCoveragePercent());
            model.addAttribute("totalAveragePercent", complianceService.getLatestTotalAveragePercent());
        }
        return "compliance-view";
    }

    // === List all ComplianceChecks ===
    @GetMapping("/checks")
    public String listChecks(Model model) {
        List<ComplianceCheck> checks = complianceCheckRepository.findAll();
        model.addAttribute("checks", checks);
        return "compliance-checks-list";
    }

    // === Create form ===
    @GetMapping("/create")
    public String createForm(Model model) {
        ComplianceCheck check = new ComplianceCheck();
        model.addAttribute("check", check);
        model.addAttribute("catalogs", catalogService.findAll());
        return "compliance-check-edit";
    }

    // === Create form alias for /checks/create ===
    @GetMapping("/checks/create")
    public String createChecksForm(Model model) {
        return createForm(model);
    }

    // === Edit form ===
    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, Model model) {
        ComplianceCheck check = complianceCheckRepository.findById(id).orElse(null);
        if (check == null) return "redirect:/compliance/checks";
        model.addAttribute("check", check);
        model.addAttribute("catalogs", catalogService.findAll());
        return "compliance-check-edit";
    }

    // === Save compliance+thresholds ===
    @PostMapping("/save")
    public String save(@RequestParam Map<String,String> all,
                       @RequestParam(required = false) Long id,
                       @RequestParam String name,
                       @RequestParam(required = false) String description,
                       @RequestParam Long securityCatalogId,
                       @ModelAttribute ComplianceCheck check,
                       @RequestParam Map<String, String> params,
                       RedirectAttributes attrs) {
        ComplianceCheck entity = (id != null) ? complianceCheckRepository.findById(id).orElse(new ComplianceCheck()) : new ComplianceCheck();
        entity.setName(name);
        entity.setDescription(description);
        entity.setSecurityCatalog(catalogService.findById(securityCatalogId).orElse(null));
        // thresholds
        List<ComplianceThreshold> thresholds = new ArrayList<>();
        int idx = 0;
        while(params.containsKey("thresholds["+idx+"].type")) {
            ComplianceThreshold t = new ComplianceThreshold();
            t.setType(params.get("thresholds["+idx+"].type"));
            t.setValue(Integer.parseInt(params.get("thresholds["+idx+"].value")));
            t.setRuleDescription(params.get("thresholds["+idx+"].ruleDescription"));
            t.setComplianceCheck(entity);
            thresholds.add(t);
            idx++;
        }
        entity.getThresholds().clear();
        entity.getThresholds().addAll(thresholds);
        complianceCheckRepository.save(entity);
        attrs.addFlashAttribute("message", "Saved.");
        return "redirect:/compliance/checks";
    }

    // === Delete compliance check ===
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, RedirectAttributes attrs) {
        complianceCheckRepository.deleteById(id);
        attrs.addFlashAttribute("message", "Deleted");
        return "redirect:/compliance/checks";
    }
}