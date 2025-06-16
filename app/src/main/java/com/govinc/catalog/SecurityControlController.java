package com.govinc.catalog;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/security-control")
public class SecurityControlController {
    @Autowired
    private SecurityControlService service;

    @Autowired
    private SecurityControlDomainService securityControlDomainService;

    @GetMapping("/list")
    public String listSecurityControls(Model model) {
        model.addAttribute("controls", service.findAll());
        return "security-controls";
    }

    @GetMapping("/edit")
    public String editSecurityControl(@RequestParam(required = false) Long id, Model model) {
        SecurityControl control = id != null ? service.findById(id).orElse(new SecurityControl()) : new SecurityControl();
        model.addAttribute("securityControl", control);
        model.addAttribute("securityControlDomains", securityControlDomainService.findAll());
        return "edit-security-control";
    }

    @PostMapping("/edit")
    public String saveSecurityControl(@ModelAttribute SecurityControl control) {
        if (control.getSecurityControlDomain() != null && control.getSecurityControlDomain().getId() != null) {
            SecurityControlDomain domain = securityControlDomainService.findById(control.getSecurityControlDomain().getId()).orElse(null);
            control.setSecurityControlDomain(domain);
        } else {
            control.setSecurityControlDomain(null);
        }
        service.save(control);
        return "redirect:/security-control/list";
    }

    @PostMapping("/delete")
    public String deleteSecurityControl(@RequestParam Long id) {
        service.deleteById(id);
        return "redirect:/security-control/list";
    }

    @GetMapping("/create")
    public String createSecurityControl(Model model) {
        model.addAttribute("securityControl", new SecurityControl());
        model.addAttribute("securityControlDomains", securityControlDomainService.findAll());
        return "edit-security-control";
    }

    @PostMapping("/import")
    public String importSecurityControls(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("message", "Please select a CSV file to upload.");
            return "redirect:/security-control/list";
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean isHeader = true;
            while ((line = br.readLine()) != null) {
                if (isHeader) {
                    isHeader = false; // skip CSV header
                    continue;
                }
                String[] columns = line.split(",");
                if (columns.length < 4) continue;
                String name = columns[0].trim();
                String description = columns[1].trim();
                String reference = columns[2].trim();
                String domainName = columns[3].trim();
                if (name.isEmpty() || domainName.isEmpty()) continue; // must have required fields
                SecurityControlDomain domain = securityControlDomainService.findAll().stream()
                    .filter(d -> d.getName().equalsIgnoreCase(domainName)).findFirst().orElse(null);
                if (domain == null) {
                    domain = new SecurityControlDomain(domainName, "");
                    domain = securityControlDomainService.save(domain);
                }
                SecurityControl sc = new SecurityControl();
                sc.setName(name);
                sc.setDetail(description);
                sc.setReference(reference);
                sc.setSecurityControlDomain(domain);
                service.save(sc);
            }
            redirectAttributes.addFlashAttribute("message", "Import successful!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("message", "Import failed: " + ex.getMessage());
        }
        return "redirect:/security-control/list";
    }
}
