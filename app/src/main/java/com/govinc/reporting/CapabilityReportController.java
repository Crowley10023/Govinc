package com.govinc.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.govinc.authorization.AuthorizationService;
import com.govinc.authorization.UnauthorizedException;
import com.govinc.catalog.SecurityCapabilityService;
import com.govinc.catalog.SecurityCatalogService;
import com.govinc.maturity.MaturityModelRepository;
import com.govinc.organization.OrgUnitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Controller
@RequestMapping("/capability-report")
public class CapabilityReportController {

    @Autowired
    private CapabilityReportService service;

    @Autowired
    private SecurityCapabilityService capabilityService;

    @Autowired
    private SecurityCatalogService catalogService;

    @Autowired
    private OrgUnitService orgUnitService;

    @Autowired
    private MaturityModelRepository maturityModelRepository;

    @Autowired
    private AuthorizationService authorizationService;

    // ─── List ────────────────────────────────────────────────────────────────

    @GetMapping("/list")
    public String list(Model model) {
        if (!authorizationService.canAccessCompliance()) {
            throw new UnauthorizedException("You do not have permission to view capability reports.");
        }
        model.addAttribute("reports", service.findAll());
        return "capability-reports";
    }

    // ─── Create / Edit ───────────────────────────────────────────────────────

    @GetMapping("/create")
    public String createForm(Model model) {
        if (!authorizationService.canAccessCompliance()) {
            throw new UnauthorizedException("You do not have permission to create capability reports.");
        }
        populateFormModel(model, new CapabilityReport());
        return "edit-capability-report";
    }

    @GetMapping("/edit")
    public String editForm(@RequestParam Long id, Model model) {
        if (!authorizationService.canAccessCompliance()) {
            throw new UnauthorizedException("You do not have permission to edit capability reports.");
        }
        CapabilityReport report = service.findById(id).orElse(new CapabilityReport());
        populateFormModel(model, report);
        return "edit-capability-report";
    }

    @PostMapping("/save")
    public String save(
            @ModelAttribute CapabilityReport report,
            @RequestParam(value = "catalogId", required = false) Long catalogId,
            @RequestParam(value = "orgUnitId", required = false) Long orgUnitId,
            @RequestParam(value = "maturityModelId", required = false) Long maturityModelId,
            @RequestParam(value = "capabilityIds", required = false) List<Long> capabilityIds) {
        if (!authorizationService.canAccessCompliance()) {
            throw new UnauthorizedException("You do not have permission to save capability reports.");
        }

        report.setSecurityCatalog(catalogId != null
                ? catalogService.findById(catalogId).orElse(null) : null);
        report.setOrgUnit(orgUnitId != null
                ? orgUnitService.getOrgUnit(orgUnitId).orElse(null) : null);
        report.setMaturityModel(maturityModelId != null
            ? maturityModelRepository.findById(maturityModelId).orElse(null) : null);

        List<com.govinc.catalog.SecurityCapability> caps = new ArrayList<>();
        if (capabilityIds != null) {
            capabilityIds.forEach(cid -> capabilityService.findById(cid).ifPresent(caps::add));
        }
        report.setCapabilities(caps);

        service.save(report);
        return "redirect:/capability-report/list";
    }

    // ─── Delete ──────────────────────────────────────────────────────────────

    @PostMapping("/delete")
    @ResponseBody
    public String delete(@RequestParam(required = false) Long id) {
        if (!authorizationService.canAccessCompliance()) {
            return buildErrorResponse("Forbidden", "You do not have permission to delete capability reports.");
        }
        if (id == null) {
            return buildErrorResponse("Invalid ID", "The provided report ID is invalid.");
        }
        try {
            service.deleteById(id);
            return buildSuccessResponse();
        } catch (DataIntegrityViolationException e) {
            return buildErrorResponse("Cannot Delete Report",
                    "This report cannot be deleted because it is still in use.");
        } catch (Exception e) {
            return buildErrorResponse("Deletion Error", "An error occurred while deleting the report.");
        }
    }

    // ─── Calculate ───────────────────────────────────────────────────────────

    @GetMapping("/calculate")
    public String calculate(@RequestParam Long id, Model model) {
        if (!authorizationService.canAccessCompliance()) {
            throw new UnauthorizedException("You do not have permission to calculate capability reports.");
        }
        try {
            CapabilityReportService.CalculationResult result = service.calculate(id);
            model.addAttribute("result", result);
            return "capability-report-view";
        } catch (NoSuchElementException e) {
            return "redirect:/capability-report/list";
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void populateFormModel(Model model, CapabilityReport report) {
        model.addAttribute("report", report);
        model.addAttribute("allCatalogs", catalogService.findAll());
        model.addAttribute("allMaturityModels", maturityModelRepository.findAll());
        model.addAttribute("allOrgUnits", orgUnitService.getAllOrgUnits());
        model.addAttribute("allCapabilities", capabilityService.findAll());
    }

    private String buildSuccessResponse() {
        try {
            Map<String, Object> r = new HashMap<>();
            r.put("success", true);
            r.put("message", "Report deleted successfully");
            return new ObjectMapper().writeValueAsString(r);
        } catch (Exception e) {
            return "{\"success\":true}";
        }
    }

    private String buildErrorResponse(String title, String message) {
        try {
            Map<String, Object> r = new HashMap<>();
            r.put("success", false);
            r.put("title", title);
            r.put("message", message);
            return new ObjectMapper().writeValueAsString(r);
        } catch (Exception e) {
            return "{\"success\":false}";
        }
    }
}
