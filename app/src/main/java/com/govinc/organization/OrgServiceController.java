package com.govinc.organization;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/orgservices")
public class OrgServiceController {
    @Autowired
    private OrgServiceService orgServiceService;
    @Autowired
    private OrgUnitService orgUnitService;

    @GetMapping({"/list", "/list.html"})
    public String listOrgServicesView(Model model) {
        List<OrgService> orgServices = orgServiceService.getAllOrgServices();
        model.addAttribute("orgServices", orgServices);
        return "orgservice-list";
    }

    @GetMapping("/create")
    public String createOrgServiceForm(Model model) {
        OrgService orgService = new OrgService();
        model.addAttribute("orgService", orgService);
        model.addAttribute("allOrgUnits", orgUnitService.getAllOrgUnits());
        return "orgservice-edit";
    }

    @GetMapping("/edit/{id}")
    public String editOrgServiceForm(@PathVariable Long id, Model model) {
        Optional<OrgService> orgServiceOpt = orgServiceService.getOrgService(id);
        if (orgServiceOpt.isEmpty()) {
            return "redirect:/orgservices/list";
        }
        OrgService orgService = orgServiceOpt.get();
        model.addAttribute("orgService", orgService);
        model.addAttribute("allOrgUnits", orgUnitService.getAllOrgUnits());
        return "orgservice-edit";
    }

    @PostMapping("/save")
    public String saveOrgService(@ModelAttribute OrgService orgService, @RequestParam(value="orgUnitIds", required=false) List<Long> orgUnitIds) {
        if (orgUnitIds != null) {
            Set<OrgUnit> selectedOrgUnits = orgUnitService.getAllOrgUnits().stream()
                .filter(ou -> orgUnitIds.contains(ou.getId()))
                .collect(Collectors.toSet());
            orgService.setOrgUnits(selectedOrgUnits);
        } else {
            orgService.setOrgUnits(null);
        }
        orgServiceService.saveOrgService(orgService);
        return "redirect:/orgservices/list";
    }

    @ResponseBody
    @GetMapping
    public List<OrgService> getAllOrgServices() {
        return orgServiceService.getAllOrgServices();
    }

    @ResponseBody
    @GetMapping("/{id}")
    public Optional<OrgService> getOrgService(@PathVariable Long id) {
        return orgServiceService.getOrgService(id);
    }

    @ResponseBody
    @PostMapping
    public OrgService addOrgService(@RequestBody OrgService orgService) {
        return orgServiceService.saveOrgService(orgService);
    }

    @ResponseBody
    @DeleteMapping("/{id}")
    public void deleteOrgService(@PathVariable Long id) {
        orgServiceService.deleteOrgService(id);
    }
}