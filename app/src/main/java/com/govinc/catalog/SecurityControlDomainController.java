package com.govinc.catalog;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import com.govinc.util.JsonResponseUtil;

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
    @ResponseBody
    public String deleteDomain(@RequestParam(required = false) Long id) {
        if (id == null) {
            return JsonResponseUtil.buildErrorResponse("Invalid ID", "The provided domain ID is invalid.");
        }
        
        try {
            service.deleteById(id);
            return JsonResponseUtil.buildSuccessResponse("Domain deleted successfully");
        } catch (DataIntegrityViolationException e) {
            // Handle foreign key constraint violations
            return JsonResponseUtil.buildErrorResponse(
                "Cannot Delete Domain",
                "This domain cannot be deleted because it is still in use. Please remove all associated security controls first."
            );
        } catch (Exception e) {
            return JsonResponseUtil.buildErrorResponse(
                "Deletion Error",
                "An error occurred while deleting the domain. Please try again later."
            );
        }
    }

    @GetMapping("/create")
    public String createDomain(Model model) {
        model.addAttribute("domain", new SecurityControlDomain());
        return "edit-security-control-domain";
    }
}
