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

    @Autowired
    private SecurityCatalogService securityCatalogService;

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
    public String importSecurityControls(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "catalogOption", defaultValue = "none") String catalogOption,
            @RequestParam(value = "existingCatalogId", required = false) Long existingCatalogId,
            @RequestParam(value = "catalogName", required = false) String catalogName,
            @RequestParam(value = "catalogDescription", required = false) String catalogDescription,
            @RequestParam(value = "catalogRevision", required = false) String catalogRevision,
            RedirectAttributes redirectAttributes) {
        
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("message", "Please select a CSV file to upload.");
            return "redirect:/security-control/list";
        }
        
        try {
            // Handle catalog creation/selection
            SecurityCatalog targetCatalog = null;
            if ("existing".equals(catalogOption) && existingCatalogId != null) {
                targetCatalog = securityCatalogService.findById(existingCatalogId).orElse(null);
                if (targetCatalog == null) {
                    redirectAttributes.addFlashAttribute("message", "Selected catalog not found.");
                    return "redirect:/security-control/list";
                }
            } else if ("new".equals(catalogOption) && catalogName != null && !catalogName.trim().isEmpty()) {
                targetCatalog = new SecurityCatalog();
                targetCatalog.setName(catalogName.trim());
                targetCatalog.setDescription(catalogDescription != null ? catalogDescription.trim() : "");
                targetCatalog.setRevision(catalogRevision != null ? catalogRevision.trim() : "");
                targetCatalog = securityCatalogService.save(targetCatalog);
            }
            
            // Import security controls
            java.util.List<SecurityControl> importedControls = new java.util.ArrayList<>();
            int successCount = 0;
            int errorCount = 0;
            
            try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                boolean isHeader = true;
                int lineNumber = 0;
                
                while ((line = br.readLine()) != null) {
                    lineNumber++;
                    if (isHeader) {
                        isHeader = false; // skip CSV header
                        continue;
                    }
                    
                    try {
                        String[] columns = parseCSVLine(line);
                        if (columns.length < 4) {
                            errorCount++;
                            continue;
                        }
                        
                        String name = columns[0].trim().replaceAll("^\"|\"$", "");
                        String description = columns[1].trim().replaceAll("^\"|\"$", "");
                        String reference = columns[2].trim().replaceAll("^\"|\"$", "");
                        String domainName = columns[3].trim().replaceAll("^\"|\"$", "");
                        
                        if (name.isEmpty() || domainName.isEmpty()) {
                            errorCount++;
                            continue; // must have required fields
                        }
                        
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
                        sc = service.save(sc);
                        
                        importedControls.add(sc);
                        successCount++;
                        
                    } catch (Exception rowEx) {
                        errorCount++;
                        // Log the error but continue processing other rows
                        System.err.println("Error processing row " + lineNumber + ": " + rowEx.getMessage());
                    }
                }
            }
            
            // Link imported controls to catalog if specified
            if (targetCatalog != null && !importedControls.isEmpty()) {
                java.util.Set<SecurityControl> catalogControls = new java.util.HashSet<>(targetCatalog.getSecurityControls());
                catalogControls.addAll(importedControls);
                targetCatalog.setSecurityControls(catalogControls);
                securityCatalogService.save(targetCatalog);
            }
            
            // Prepare success message
            String message = String.format("Import completed! Successfully imported %d security controls.", successCount);
            if (errorCount > 0) {
                message += String.format(" %d rows had errors and were skipped.", errorCount);
            }
            if (targetCatalog != null) {
                message += String.format(" All controls linked to catalog '%s'.", targetCatalog.getName());
            }
            
            redirectAttributes.addFlashAttribute("message", message);
            
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("message", "Import failed: " + ex.getMessage());
            ex.printStackTrace();
        }
        
        return "redirect:/security-control/list";
    }
    
    // Helper method to parse CSV line with proper quote handling
    private String[] parseCSVLine(String line) {
        java.util.List<String> result = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        
        return result.toArray(new String[0]);
    }
}
