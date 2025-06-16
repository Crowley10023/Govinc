package com.govinc.catalog;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.govinc.maturity.MaturityModel;
import com.govinc.maturity.MaturityModelRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/security-catalog")
public class SecurityCatalogController {
    @Autowired
    private SecurityCatalogService service;

    @Autowired
    private SecurityControlService securityControlService;

    @Autowired
    private MaturityModelRepository maturityModelRepository;

    @GetMapping("/list")
    public String listSecurityCatalogs(Model model) {
        model.addAttribute("catalogs", service.findAll());
        return "security-catalogs";
    }

    @GetMapping("/edit")
    public String editSecurityCatalog(@RequestParam(required = false) Long id, Model model) {
        SecurityCatalog catalog = id != null ? service.findById(id).orElse(new SecurityCatalog()) : new SecurityCatalog();
        model.addAttribute("securityCatalog", catalog);
        model.addAttribute("securityControls", securityControlService.findAll()); // for dropdown
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
        model.addAttribute("securityControls", securityControlService.findAll());
        model.addAttribute("maturityModels", maturityModelRepository.findAll());
        return "edit-security-catalog";
    }
}
