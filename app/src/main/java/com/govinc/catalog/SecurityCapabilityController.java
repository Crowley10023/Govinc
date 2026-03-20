package com.govinc.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.govinc.authorization.AuthorizationService;
import com.govinc.authorization.UnauthorizedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping("/security-capability")
public class SecurityCapabilityController {

    @Autowired
    private SecurityCapabilityService service;

    @Autowired
    private SecurityCatalogService catalogService;

    @Autowired
    private SecurityControlDomainService domainService;

    @Autowired
    private AuthorizationService authorizationService;

    @GetMapping("/list")
    public String list(Model model) {
        if (!authorizationService.canAccessSecurityFramework()) {
            throw new UnauthorizedException("You do not have permission to view security capabilities.");
        }
        model.addAttribute("capabilities", service.findAll());
        return "security-capabilities";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        if (!authorizationService.canAccessSecurityFramework()) {
            throw new UnauthorizedException("You do not have permission to create security capabilities.");
        }
        model.addAttribute("capability", new SecurityCapability());
        model.addAttribute("allCatalogs", catalogService.findAll());
        model.addAttribute("allDomains", domainService.findAll());
        return "edit-security-capability";
    }

    @GetMapping("/edit")
    public String editForm(@RequestParam(required = false) Long id, Model model) {
        if (!authorizationService.canAccessSecurityFramework()) {
            throw new UnauthorizedException("You do not have permission to edit security capabilities.");
        }
        SecurityCapability capability = id != null
                ? service.findById(id).orElse(new SecurityCapability())
                : new SecurityCapability();
        model.addAttribute("capability", capability);
        model.addAttribute("allCatalogs", catalogService.findAll());
        model.addAttribute("allDomains", domainService.findAll());
        return "edit-security-capability";
    }

    @PostMapping("/save")
    public String save(
            @ModelAttribute SecurityCapability capability,
            @RequestParam(value = "catalogId", required = false) Long catalogId,
            @RequestParam(value = "domainIds", required = false) List<Long> domainIds) {
        if (!authorizationService.canAccessSecurityFramework()) {
            throw new UnauthorizedException("You do not have permission to save security capabilities.");
        }
        if (catalogId != null) {
            catalogService.findById(catalogId).ifPresent(capability::setSecurityCatalog);
        } else {
            capability.setSecurityCatalog(null);
        }
        Set<SecurityControlDomain> domains = new HashSet<>();
        if (domainIds != null) {
            domainIds.forEach(did -> domainService.findById(did).ifPresent(domains::add));
        }
        capability.setDomains(domains);
        service.save(capability);
        return "redirect:/security-capability/list";
    }

    @PostMapping("/delete")
    @ResponseBody
    public String delete(@RequestParam(required = false) Long id) {
        if (!authorizationService.canAccessSecurityFramework()) {
            return buildErrorResponse("Forbidden", "You do not have permission to delete security capabilities.");
        }
        if (id == null) {
            return buildErrorResponse("Invalid ID", "The provided capability ID is invalid.");
        }
        try {
            service.deleteById(id);
            return buildSuccessResponse();
        } catch (DataIntegrityViolationException e) {
            return buildErrorResponse("Cannot Delete Capability",
                    "This capability cannot be deleted because it is referenced by a capability report.");
        } catch (Exception e) {
            return buildErrorResponse("Deletion Error", "An error occurred while deleting the capability.");
        }
    }

    private String buildSuccessResponse() {
        try {
            Map<String, Object> r = new HashMap<>();
            r.put("success", true);
            r.put("message", "Capability deleted successfully");
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
