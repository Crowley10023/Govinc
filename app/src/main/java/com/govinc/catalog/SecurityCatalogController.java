package com.govinc.catalog;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.govinc.maturity.MaturityModel;
import com.govinc.maturity.MaturityModelRepository;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/security-catalog")
public class SecurityCatalogController {
    @Autowired
    private SecurityCatalogService service;

    @Autowired
    private SecurityControlService securityControlService;

    @Autowired
    private SecurityControlDomainService securityControlDomainService;

    @Autowired
    private MaturityModelRepository maturityModelRepository;

    // Helper: All security controls by domain, also handles unassigned controls
    private List<SecurityControlDomain> getAllDomainsWithAllControlsGrouped() {
        List<SecurityControlDomain> domains = securityControlDomainService.findAll();
        List<SecurityControl> allControls = securityControlService.findAll();
        Map<Long, SecurityControlDomain> domainMap = domains.stream().collect(Collectors.toMap(SecurityControlDomain::getId, d -> d));

        // Track assigned controls
        Set<Long> assignedControlIds = new HashSet<>();
        for (SecurityControlDomain domain : domains) {
            Set<SecurityControl> controlsForDomain = new HashSet<>();
            for (SecurityControl control : domain.getSecurityControls()) {
                controlsForDomain.add(control);
                assignedControlIds.add(control.getId());
            }
            domain.setSecurityControls(controlsForDomain);
        }

        // Add a synthetic domain for unassigned controls if necessary
        List<SecurityControl> unassignedControls = allControls.stream()
                .filter(ctrl -> ctrl.getSecurityControlDomain() == null || !domainMap.containsKey(ctrl.getSecurityControlDomain().getId()))
                .collect(Collectors.toList());

        if (!unassignedControls.isEmpty()) {
            SecurityControlDomain unassigned = new SecurityControlDomain();
            unassigned.setName("Unassigned");
            unassigned.setDescription("Security controls that are not assigned to any domain.");
            unassigned.setSecurityControls(new HashSet<>(unassignedControls));
            domains = new ArrayList<>(domains); // ensure it's mutable
            domains.add(unassigned);
        }
        return domains;
    }

    @GetMapping("/list")
    public String listSecurityCatalogs(Model model) {
        model.addAttribute("catalogs", service.findAll());
        return "security-catalogs";
    }

    @GetMapping("/edit")
    public String editSecurityCatalog(@RequestParam(required = false) Long id, Model model) {
        SecurityCatalog catalog = id != null ? service.findById(id).orElse(new SecurityCatalog()) : new SecurityCatalog();
        model.addAttribute("securityCatalog", catalog);
        model.addAttribute("securityControlDomains", getAllDomainsWithAllControlsGrouped());
        model.addAttribute("maturityModels", maturityModelRepository.findAll());
        return "edit-security-catalog";
    }

    @PostMapping("/edit")
    public String saveSecurityCatalog(
            @ModelAttribute SecurityCatalog catalog,
            @RequestParam Long maturityModelId,
            @RequestParam(value = "securityControls", required = false) List<Long> securityControlIds,
            Model model) {
        MaturityModel maturityModel = maturityModelRepository.findById(maturityModelId).orElse(null);
        catalog.setMaturityModel(maturityModel);

        // Convert received IDs to Set<SecurityControl>
        Set<SecurityControl> selectedControls = new HashSet<>();
        if (securityControlIds != null) {
            selectedControls = securityControlService.findAll().stream()
                    .filter(control -> securityControlIds.contains(control.getId()))
                    .collect(Collectors.toSet());
        }
        catalog.setSecurityControls(selectedControls);

        service.save(catalog);
        return "redirect:/security-catalog/list";
    }

    @PostMapping("/delete")
    public String deleteSecurityCatalog(@RequestParam Long id) {
        service.deleteById(id);
        return "redirect:/security-catalog/list";
    }

    @GetMapping("/create")
    public String createSecurityCatalog(Model model) {
        model.addAttribute("securityCatalog", new SecurityCatalog());
        model.addAttribute("securityControlDomains", getAllDomainsWithAllControlsGrouped());
        model.addAttribute("maturityModels", maturityModelRepository.findAll());
        return "edit-security-catalog";
    }
}
