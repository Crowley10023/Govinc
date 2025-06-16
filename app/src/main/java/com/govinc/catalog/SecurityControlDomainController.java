package com.govinc.catalog;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/security-control-domain")
public class SecurityControlDomainController {
    @Autowired
    private SecurityControlDomainService service;

    @GetMapping("/list")
    public String listDomains(Model model) {
        model.addAttribute("domains", service.findAll());
        return "security-control-domains";
    }

    @GetMapping("/edit")
    public String editDomain(@RequestParam(required = false) Long id, Model model) {
        SecurityControlDomain domain = id != null ? service.findById(id).orElse(new SecurityControlDomain()) : new SecurityControlDomain();
        model.addAttribute("domain", domain);
        return "edit-security-control-domain";
    }

    @PostMapping("/edit")
    public String saveDomain(@ModelAttribute SecurityControlDomain domain) {
        service.save(domain);
        return "redirect:/security-control-domain/list";
    }

    @PostMapping("/delete")
    public String deleteDomain(@RequestParam Long id) {
        service.deleteById(id);
        return "redirect:/security-control-domain/list";
    }

    @GetMapping("/create")
    public String createDomain(Model model) {
        model.addAttribute("domain", new SecurityControlDomain());
        return "edit-security-control-domain";
    }
}
