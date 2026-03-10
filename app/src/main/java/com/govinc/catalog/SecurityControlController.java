package com.govinc.catalog;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;

@Controller
@RequestMapping("/security-control")
public class SecurityControlController {
    @Autowired
    private SecurityControlService service;

    @Autowired
    private SecurityControlDomainService securityControlDomainService;

    @Autowired
    private SecurityCatalogService securityCatalogService;

    @Autowired
    private com.govinc.organization.OrgServiceAssessmentService orgServiceAssessmentService;

    @GetMapping("/list")
    public String listSecurityControls(Model model) {
        model.addAttribute("controls", service.findAll());
        return "security-controls";
    }

    @GetMapping("/orgservice-mapping")
    public String orgServiceMapping(Model model) {
        List<SecurityControl> allControls = service.findAll();
        
        // Get mapping data: control -> service that has applicable assessment
        java.util.Map<Long, Long> controlServiceMap = new java.util.HashMap<>();
        
        // Check all assessments to build the current mappings
        List<com.govinc.organization.OrgServiceAssessment> allAssessments = 
            orgServiceAssessmentService.getAllAssessments();
        
        for (com.govinc.organization.OrgServiceAssessment assessment : allAssessments) {
            for (com.govinc.organization.OrgServiceAssessmentControl control : assessment.getControls()) {
                // If a control is applicable in an assessment, it's mapped to that service
                if (control.isApplicable()) {
                    controlServiceMap.put(control.getSecurityControl().getId(), assessment.getOrgService().getId());
                }
            }
        }
        
        model.addAttribute("controls", allControls);
        model.addAttribute("controlServiceMap", controlServiceMap);
        return "security-controls-orgservice-mapping";
    }

    @PostMapping("/map-service")
    @ResponseBody
    public Map<String, Object> mapServiceToControl(@RequestParam Long controlId, @RequestParam(required = false) String serviceId) {
        Map<String, Object> response = new java.util.HashMap<>();
        try {
            if (serviceId == null || serviceId.isEmpty()) {
                // Clearing selection - find and clear applicable status
                List<com.govinc.organization.OrgServiceAssessment> allAssessments =
                    orgServiceAssessmentService.getAllAssessments();

                for (com.govinc.organization.OrgServiceAssessment assessment : allAssessments) {
                    boolean changed = false;
                    for (com.govinc.organization.OrgServiceAssessmentControl control : assessment.getControls()) {
                        if (control.getSecurityControl().getId().equals(controlId) && control.isApplicable()) {
                            control.setApplicable(false);
                            control.setPercent(0);
                            changed = true;
                        }
                    }
                    if (changed) {
                        orgServiceAssessmentService.saveAssessmentWithoutValidation(assessment);
                    }
                }

                response.put("success", true);
                response.put("message", "Service selection cleared");
            } else {
                Long parsedServiceId = Long.parseLong(serviceId);

                // Ensure target service has an assessment and a row for this control
                com.govinc.organization.OrgServiceAssessment targetAssessment =
                    orgServiceAssessmentService.findOrCreateAssessment(parsedServiceId);

                boolean targetHasControl = targetAssessment.getControls().stream()
                    .anyMatch(c -> c.getSecurityControl().getId().equals(controlId));

                if (!targetHasControl) {
                    SecurityControl securityControl = service.findById(controlId)
                        .orElseThrow(() -> new IllegalArgumentException("Invalid controlId: " + controlId));

                    com.govinc.organization.OrgServiceAssessmentControl newControl =
                        new com.govinc.organization.OrgServiceAssessmentControl();
                    newControl.setOrgServiceAssessment(targetAssessment);
                    newControl.setSecurityControl(securityControl);
                    newControl.setApplicable(false);
                    newControl.setPercent(0);
                    targetAssessment.getControls().add(newControl);
                    orgServiceAssessmentService.saveAssessmentWithoutValidation(targetAssessment);
                }

                // First, clear this control from all other assessments, then set for the target service
                List<com.govinc.organization.OrgServiceAssessment> allAssessments =
                    orgServiceAssessmentService.getAllAssessments();

                for (com.govinc.organization.OrgServiceAssessment assessment : allAssessments) {
                    boolean changed = false;
                    for (com.govinc.organization.OrgServiceAssessmentControl control : assessment.getControls()) {
                        if (control.getSecurityControl().getId().equals(controlId)) {
                            if (assessment.getOrgService().getId().equals(parsedServiceId)) {
                                // Set as applicable for target service
                                if (!control.isApplicable()) {
                                    control.setApplicable(true);
                                    if (control.getPercent() == 0) {
                                        control.setPercent(50); // Default percent when setting as applicable
                                    }
                                    changed = true;
                                }
                            } else if (control.isApplicable()) {
                                // Clear from other services
                                control.setApplicable(false);
                                control.setPercent(0);
                                changed = true;
                            }
                        }
                    }
                    if (changed) {
                        orgServiceAssessmentService.saveAssessmentWithoutValidation(assessment);
                    }
                }

                response.put("success", true);
                response.put("message", "Control mapped to service");
                response.put("controlId", controlId);
                response.put("serviceId", parsedServiceId);
            }
        } catch (Exception ex) {
            System.err.println("Error in mapServiceToControl: " + ex.getMessage());
            ex.printStackTrace();
            response.put("success", false);
            response.put("message", ex.getMessage());
        }
        return response;
    }

    @GetMapping("/edit")
    public String editSecurityControl(@RequestParam(required = false) Long id, Model model) {
        SecurityControl control = id != null ? service.findById(id).orElse(new SecurityControl())
                : new SecurityControl();
        model.addAttribute("securityControl", control);
        model.addAttribute("securityControlDomains", securityControlDomainService.findAll());

        // Collect distinct existing tags from all security controls (non-null, non-empty),
        // splitting comma-separated tag lists like "SOC, MDR" into individual tags.
        List<String> existingTags = service.findAll().stream()
                .map(SecurityControl::getTag)
                .filter(tag -> tag != null && !tag.trim().isEmpty())
                .flatMap(tag -> java.util.Arrays.stream(tag.split(",")))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .sorted(String::compareToIgnoreCase)
                .toList();
        model.addAttribute("existingTags", existingTags);

        return "security-control-editor";
    }

    @PostMapping("/edit")
    public String saveSecurityControl(@ModelAttribute SecurityControl control) {
        if (control.getSecurityControlDomain() != null && control.getSecurityControlDomain().getId() != null) {
            SecurityControlDomain domain = securityControlDomainService
                    .findById(control.getSecurityControlDomain().getId()).orElse(null);
            control.setSecurityControlDomain(domain);
        } else {
            control.setSecurityControlDomain(null);
        }
        service.save(control);
        return "redirect:/security-control/list";
    }

    @PostMapping("/delete")
    public String deleteSecurityControl(@RequestParam Long id, RedirectAttributes redirectAttributes) {
        try {
            service.deleteById(id);
            redirectAttributes.addFlashAttribute("message", "Security control deleted successfully.");
            redirectAttributes.addFlashAttribute("messageType", "success");
        } catch (SecurityControlService.SecurityControlInUseException e) {
            redirectAttributes.addFlashAttribute("message", e.getMessage());
            redirectAttributes.addFlashAttribute("messageType", "error");
        }
        return "redirect:/security-control/list";
    }

    @PostMapping("/delete/api")
    @ResponseBody
    public Map<String, Object> deleteSecurityControlApi(@RequestParam Long id) {
        Map<String, Object> response = new java.util.HashMap<>();
        try {
            service.deleteById(id);
            response.put("success", true);
            response.put("message", "Security control deleted successfully.");
        } catch (SecurityControlService.SecurityControlInUseException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            response.put("errorType", "IN_USE");
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Handle foreign key constraint violations
            String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            String userMessage;

            if (message.contains("foreign key constraint") || message.contains("constraint")) {
                userMessage = "Cannot delete this security control because it is currently linked to one or more catalogs or used in assessments. "
                        +
                        "Please unlink it from all catalogs and remove it from all assessments first.";
            } else {
                userMessage = "Cannot delete this security control due to a data constraint. " +
                        "It may be referenced elsewhere in the system.";
            }

            response.put("success", false);
            response.put("message", userMessage);
            response.put("errorType", "CONSTRAINT_VIOLATION");
            System.err.println("[DELETE] DataIntegrityViolationException: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            response.put("success", false);
            String userMessage = "An unexpected error occurred while deleting the security control. " +
                    "Please try again or contact support if the problem persists.";
            response.put("message", userMessage);
            response.put("errorType", "GENERAL_ERROR");
            System.err.println("[DELETE] Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
        return response;
    }

    @GetMapping("/create")
    public String createSecurityControl(Model model) {
        model.addAttribute("securityControl", new SecurityControl());
        model.addAttribute("securityControlDomains", securityControlDomainService.findAll());

        // Collect distinct existing tags from all security controls (non-null, non-empty),
        // splitting comma-separated tag lists like "SOC, MDR" into individual tags.
        List<String> existingTags = service.findAll().stream()
                .map(SecurityControl::getTag)
                .filter(tag -> tag != null && !tag.trim().isEmpty())
                .flatMap(tag -> java.util.Arrays.stream(tag.split(",")))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .sorted(String::compareToIgnoreCase)
                .toList();
        model.addAttribute("existingTags", existingTags);

        return "security-control-editor";
    }

    @PostMapping("/import")
    public String importSecurityControls(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "catalogOption", defaultValue = "none") String catalogOption,
            @RequestParam(value = "existingCatalogId", required = false) String existingCatalogIdStr,
            @RequestParam(value = "catalogName", required = false) String catalogName,
            @RequestParam(value = "catalogDescription", required = false) String catalogDescription,
            @RequestParam(value = "catalogRevision", required = false) String catalogRevision,
            @RequestParam(value = "mergeActions", required = false) String mergeActionsJson,
            RedirectAttributes redirectAttributes) {

        System.out.println("\n========================================");
        System.out.println("[IMPORT] POST /security-control/import called");
        System.out.println("[IMPORT] File: " + (file != null ? file.getOriginalFilename() : "null"));
        System.out.println("[IMPORT] Catalog Option: " + catalogOption);
        System.out.println("[IMPORT] Merge Actions JSON: " + mergeActionsJson);
        System.out.println("========================================");

        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("message", "Please select a CSV file to upload.");
            return "redirect:/security-control/list";
        }

        // Parse merge actions if provided
        java.util.Map<String, java.util.Map<String, String>> mergeActions = new java.util.HashMap<>();
        if (mergeActionsJson != null && !mergeActionsJson.isEmpty() && !mergeActionsJson.equals("{}")) {
            try {
                mergeActions = parseJsonMergeActions(mergeActionsJson);
                System.out.println("[IMPORT] Parsed " + mergeActions.size() + " merge actions");
                for (java.util.Map.Entry<String, java.util.Map<String, String>> entry : mergeActions.entrySet()) {
                    System.out.println("[IMPORT]   " + entry.getKey() + " -> " + entry.getValue());
                }
            } catch (Exception e) {
                System.err.println("[IMPORT] Error parsing merge actions: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Parse existingCatalogId
        Long existingCatalogId = null;
        if (existingCatalogIdStr != null && !existingCatalogIdStr.isEmpty()) {
            try {
                existingCatalogId = Long.parseLong(existingCatalogIdStr);
            } catch (Exception e) {
                System.err.println("[IMPORT] Invalid catalog ID: " + existingCatalogIdStr);
            }
        }

        try {
            System.out.println("[IMPORT] Starting import process");

            // Handle catalog creation/selection
            SecurityCatalog targetCatalog = null;
            if ("existing".equals(catalogOption) && existingCatalogId != null) {
                targetCatalog = securityCatalogService.findById(existingCatalogId).orElse(null);
                if (targetCatalog == null) {
                    System.err.println("[IMPORT] Selected catalog not found: " + existingCatalogId);
                    redirectAttributes.addFlashAttribute("message", "Selected catalog not found.");
                    return "redirect:/security-control/list";
                }
                System.out.println("[IMPORT] Using existing catalog: " + targetCatalog.getName());
            } else if ("new".equals(catalogOption) && catalogName != null && !catalogName.trim().isEmpty()) {
                targetCatalog = new SecurityCatalog();
                targetCatalog.setName(catalogName.trim());
                targetCatalog.setDescription(catalogDescription != null ? catalogDescription.trim() : "");
                targetCatalog.setRevision(catalogRevision != null ? catalogRevision.trim() : "");
                targetCatalog = securityCatalogService.save(targetCatalog);
                System.out.println("[IMPORT] Created new catalog: " + targetCatalog.getName());
            }

            // Import security controls
            java.util.List<SecurityControl> importedControls = new java.util.ArrayList<>();
            int successCount = 0;
            int errorCount = 0;
            int skipCount = 0;

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                boolean isHeader = true;
                int rowNumber = 0;

                // Parse the entire CSV file properly (handling multiline quoted fields)
                String csvContent = readAllContent(br);
                List<String[]> allRows = parseCSVContent(csvContent);
                System.out.println("[IMPORT] Total rows parsed: " + allRows.size());

                for (int rowIdx = 0; rowIdx < allRows.size(); rowIdx++) {
                    if (isHeader) {
                        isHeader = false; // skip CSV header
                        System.out.println("[IMPORT] Skipping header row");
                        continue;
                    }

                    rowNumber = rowIdx; // Data row number (after header)

                    try {
                        String[] columns = allRows.get(rowIdx);
                        System.out.println("[IMPORT] Processing row " + rowNumber + ": " + columns.length + " columns");

                        if (columns.length < 4) {
                            errorCount++;
                            System.out.println("[IMPORT] Row " + rowNumber + ": Skipped (insufficient columns)");
                            continue;
                        }

                        String originalName = columns[0].trim().replaceAll("^\"|\"$", "");
                        String name = originalName;
                        String description = columns[1].trim().replaceAll("^\"|\"$", "");
                        String reference = columns[2].trim().replaceAll("^\"|\"$", "");
                        String domainName = columns[3].trim().replaceAll("^\"|\"$", "");

                        if (name.isEmpty() || domainName.isEmpty()) {
                            errorCount++;
                            System.out.println("[IMPORT] Row " + rowNumber + ": Skipped (missing required fields)");
                            continue; // must have required fields
                        }

                        // Check merge actions and get translated data if available
                        // Use ORIGINAL name to look up in mergeActions map
                        java.util.Map<String, String> actionInfo = mergeActions.get(originalName);
                        String mergeAction = null;
                        if (actionInfo != null) {
                            mergeAction = actionInfo.get("action");
                            // Use translated data if available
                            String translatedName = actionInfo.get("translatedName");
                            String translatedDetail = actionInfo.get("translatedDetail");
                            String translatedDomainName = actionInfo.get("translatedDomainName");
                            if (translatedName != null && !translatedName.isEmpty()) {
                                name = translatedName;
                            }
                            if (translatedDetail != null && !translatedDetail.isEmpty()) {
                                description = translatedDetail;
                            }
                            if (translatedDomainName != null && !translatedDomainName.isEmpty()) {
                                domainName = translatedDomainName;
                            }
                        }
                        System.out.println("[IMPORT] Row " + rowNumber + ": Original name='" + originalName + "', Merge action=" + mergeAction);

                        // Check if this control should be skipped
                        if ("skip".equals(mergeAction)) {
                            skipCount++;
                            System.out.println("[IMPORT] Row " + rowNumber + ": Skipped (user action) - " + name);
                            continue;
                        }

                        // Check if this control should be merged with existing control
                        SecurityControl sc = null;
                        boolean isMerge = mergeAction != null && mergeAction.startsWith("merge:");

                        if (isMerge) {
                            // Merge with existing control - link to existing control without creating new one
                            try {
                                Long existingControlId = Long.parseLong(mergeAction.substring(6));
                                java.util.Optional<SecurityControl> existingOpt = service.findById(existingControlId);
                                if (existingOpt.isPresent()) {
                                    sc = existingOpt.get();
                                    System.out.println("[IMPORT] Row " + rowNumber
                                            + ": Merging with existing control ID " + existingControlId);
                                    System.out.println("[IMPORT] Row " + rowNumber
                                            + ": Keeping existing control with domain: " + 
                                            (sc.getSecurityControlDomain() != null ? sc.getSecurityControlDomain().getName() : "(none)"));
                                    // NOTE: Do NOT persist the merged control. It is used as-is from the database.
                                    // The imported data is NOT persisted, only the catalog link is created.
                                } else {
                                    // Merge target not found, create new
                                    System.out.println(
                                            "[IMPORT] Row " + rowNumber + ": Merge target not found, creating new control with new domain");
                                    sc = createNewSecurityControl(name, description, reference, domainName);
                                }
                            } catch (Exception mergeEx) {
                                // If merge fails, create new
                                System.err
                                        .println("[IMPORT] Row " + rowNumber + ": Merge failed, creating new control with new domain");
                                sc = createNewSecurityControl(name, description, reference, domainName);
                            }
                        } else {
                            // Create new control - always create new domain for non-merged controls
                            System.out.println("[IMPORT] Row " + rowNumber + ": Creating new control with domain: " + domainName);
                            sc = createNewSecurityControl(name, description, reference, domainName);
                        }

                        importedControls.add(sc);
                        successCount++;
                        System.out.println("[IMPORT] Row " + rowNumber + ": Added control to import list. Total: " + importedControls.size());

                    } catch (Exception rowEx) {
                        errorCount++;
                        // Log the error but continue processing other rows
                        System.err.println("[IMPORT] Error processing row " + rowNumber + ": " + rowEx.getMessage());
                        rowEx.printStackTrace();
                    }
                }
            }

            System.out.println("[IMPORT] Import loop complete. Total imported controls: " + importedControls.size());

            // Link imported controls to catalog if specified
            if (targetCatalog != null && !importedControls.isEmpty()) {
                System.out.println("[IMPORT] Linking " + importedControls.size() + " controls to catalog: " + targetCatalog.getName());
                java.util.Set<SecurityControl> catalogControls = new java.util.HashSet<>(
                        targetCatalog.getSecurityControls());
                System.out.println("[IMPORT] Catalog currently has " + catalogControls.size() + " controls");
                catalogControls.addAll(importedControls);
                System.out.println("[IMPORT] After adding imports, catalog will have " + catalogControls.size() + " controls");
                targetCatalog.setSecurityControls(catalogControls);
                securityCatalogService.save(targetCatalog);
                System.out.println("[IMPORT] Linked " + importedControls.size() + " controls to catalog: "
                        + targetCatalog.getName());
            } else if (targetCatalog == null) {
                System.out.println("[IMPORT] No target catalog specified, controls not linked to any catalog");
            } else if (importedControls.isEmpty()) {
                System.out.println("[IMPORT] No controls imported, nothing to link to catalog");
            }

            // Prepare success message
            String message = String.format("Import completed! Successfully imported %d security controls.",
                    successCount);
            if (skipCount > 0) {
                message += String.format(" %d rows were skipped.", skipCount);
            }
            if (errorCount > 0) {
                message += String.format(" %d rows had errors.", errorCount);
            }
            if (targetCatalog != null) {
                message += String.format(" All controls linked to catalog '%s'.", targetCatalog.getName());
            }

            System.out.println("[IMPORT] " + message);
            redirectAttributes.addFlashAttribute("message", message);

        } catch (Exception ex) {
            System.err.println("[IMPORT] Import failed with exception: " + ex.getMessage());
            ex.printStackTrace();
            redirectAttributes.addFlashAttribute("message", "Import failed: " + ex.getMessage());
        }

        System.out.println("[IMPORT] Redirecting to /security-control/list");
        System.out.println("========================================\n");
        return "redirect:/security-control/list";
    }

    // Helper method to create a new security control with domain management
    // Called when a security control is NOT merged, ensuring:
    // 1. A new SecurityControl is created and persisted to database
    // 2. A new SecurityControlDomain is created (if it doesn't already exist)
    // 3. The new control links to the domain (existing or newly created)
    private SecurityControl createNewSecurityControl(String name, String description, String reference, String domainName) {
        final String domainNameForFilter = domainName;
        SecurityControlDomain domain = securityControlDomainService.findAll().stream()
                .filter(d -> d.getName().equalsIgnoreCase(domainNameForFilter)).findFirst().orElse(null);
        
        if (domain == null) {
            // Create new domain for non-merged controls
            domain = new SecurityControlDomain(domainName, "");
            domain = securityControlDomainService.save(domain);
            System.out.println("[IMPORT]   Created new domain: " + domainName);
        } else {
            System.out.println("[IMPORT]   Using existing domain: " + domainName);
        }

        // Create and persist new security control
        SecurityControl sc = new SecurityControl();
        sc.setName(name);
        sc.setDetail(description);
        sc.setReference(reference);
        sc.setSecurityControlDomain(domain);
        sc = service.save(sc);
        System.out.println("[IMPORT]   Persisted new control: " + name + " with domain: " + domainName);
        return sc;
    }

    // Helper method to parse merge actions from JSON (with translation data)
    private java.util.Map<String, java.util.Map<String, String>> parseJsonMergeActions(String json) {
        java.util.Map<String, java.util.Map<String, String>> actions = new java.util.HashMap<>();
        // Parse JSON that contains both action and translation data
        try {
            System.out.println("[PARSE] Parsing merge actions JSON: " + json);
            // Extract each control entry
            java.util.regex.Pattern controlPattern = java.util.regex.Pattern.compile(
                    "\"([^\"]+?)\":\\s*\\{([^}]+)\\}");
            java.util.regex.Matcher controlMatcher = controlPattern.matcher(json);

            while (controlMatcher.find()) {
                String controlName = controlMatcher.group(1);
                String controlData = controlMatcher.group(2);

                // Parse action, translatedName, translatedDetail, translatedDomainName from controlData
                String action = extractJsonValue(controlData, "action");
                String translatedName = extractJsonValue(controlData, "translatedName");
                String translatedDetail = extractJsonValue(controlData, "translatedDetail");
                String translatedDomainName = extractJsonValue(controlData, "translatedDomainName");

                java.util.Map<String, String> controlInfo = new java.util.HashMap<>();
                controlInfo.put("action", action);
                if (translatedName != null && !translatedName.isEmpty()) {
                    controlInfo.put("translatedName", translatedName);
                }
                if (translatedDetail != null && !translatedDetail.isEmpty()) {
                    controlInfo.put("translatedDetail", translatedDetail);
                }
                if (translatedDomainName != null && !translatedDomainName.isEmpty()) {
                    controlInfo.put("translatedDomainName", translatedDomainName);
                }

                actions.put(controlName, controlInfo);
                System.out.println("[PARSE]   " + controlName + " -> action: " + action +
                        ", translated: " + (translatedName != null && !translatedName.isEmpty()));
            }
            System.out.println("[PARSE] Total actions parsed: " + actions.size());
        } catch (Exception e) {
            System.err.println("[PARSE] Error parsing JSON merge actions: " + e.getMessage());
            e.printStackTrace();
        }
        return actions;
    }

    // Helper to extract JSON string value
    private String extractJsonValue(String json, String key) {
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "\"" + key + "\":\\s*\"([^\"]*?)\"");
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception e) {
            System.err.println("[PARSE] Error extracting " + key + ": " + e.getMessage());
        }
        return null;
    }

    // Helper method to read all content from BufferedReader
    private String readAllContent(BufferedReader br) throws java.io.IOException {
        StringBuilder content = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            content.append(line).append("\n");
        }
        return content.toString();
    }

    // Helper method to parse CSV content with proper quote handling (RFC 4180)
    // Handles quoted fields that can contain newlines, commas, and escaped quotes
    private java.util.List<String[]> parseCSVContent(String csvContent) {
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        java.util.List<String> fields = new java.util.ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;

        int i = 0;
        while (i < csvContent.length()) {
            char c = csvContent.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < csvContent.length() && csvContent.charAt(i + 1) == '"') {
                    // Escaped quote ("")
                    currentField.append('"');
                    i += 2;
                    continue;
                } else {
                    // Toggle quote state
                    inQuotes = !inQuotes;
                    i++;
                    continue;
                }
            }

            if (c == ',' && !inQuotes) {
                // End of field
                fields.add(currentField.toString().trim());
                currentField = new StringBuilder();
                i++;
                continue;
            }

            if ((c == '\n' || c == '\r') && !inQuotes) {
                // End of row
                if (!currentField.toString().isEmpty() || fields.size() > 0) {
                    fields.add(currentField.toString().trim());
                    if (fields.size() > 0 && fields.stream().anyMatch(f -> !f.isEmpty())) {
                        rows.add(fields.toArray(new String[0]));
                    }
                    fields = new java.util.ArrayList<>();
                    currentField = new StringBuilder();
                }
                // Skip both \r and \n for Windows line endings
                if (c == '\r' && i + 1 < csvContent.length() && csvContent.charAt(i + 1) == '\n') {
                    i += 2;
                } else {
                    i++;
                }
                continue;
            }

            currentField.append(c);
            i++;
        }

        // Add last field and row if content doesn't end with newline
        if (!currentField.toString().isEmpty() || fields.size() > 0) {
            fields.add(currentField.toString().trim());
            if (fields.size() > 0 && fields.stream().anyMatch(f -> !f.isEmpty())) {
                rows.add(fields.toArray(new String[0]));
            }
        }

        System.out.println("[PARSE] CSV parsing complete: " + rows.size() + " rows parsed");
        return rows;
    }

    // Helper method to parse CSV line with proper quote handling (kept for
    // reference/legacy)
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
