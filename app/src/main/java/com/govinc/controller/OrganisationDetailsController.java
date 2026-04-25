package com.govinc.controller;

import com.govinc.entity.OrganisationDetails;
import com.govinc.entity.OrganisationDetailsRepository;
import com.govinc.assessment.AssessmentReporterWord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;

@Controller
@RequestMapping("/config/organisation-details")
public class OrganisationDetailsController {
    @Autowired
    private OrganisationDetailsRepository organisationDetailsRepository;

    @Autowired
    private AssessmentReporterWord assessmentReporterWord;

    // Directory for storing uploaded templates
    private static final String TEMPLATE_UPLOAD_DIR = "templates/uploads";

    @GetMapping
    public String getOrgDetails(Model model) {
        OrganisationDetails details = organisationDetailsRepository.findAll().stream().findFirst().orElse(new OrganisationDetails());
        model.addAttribute("organisationDetails", details);
        
        // Check if template exists
        if (details.getWordTemplatePath() != null && !details.getWordTemplatePath().isEmpty()) {
            File templateFile = new File(details.getWordTemplatePath());
            if (templateFile.exists()) {
                model.addAttribute("hasExistingTemplate", true);
                model.addAttribute("currentTemplateName", details.getWordTemplateFilename() != null ? details.getWordTemplateFilename() : "Unknown");
            } else {
                model.addAttribute("hasExistingTemplate", false);
            }
        } else {
            model.addAttribute("hasExistingTemplate", false);
        }
        
        return "organisation-details";
    }

    @PostMapping
    public String saveOrgDetails(@ModelAttribute OrganisationDetails organisationDetails, Model model) {
        OrganisationDetails persisted = organisationDetailsRepository.findAll().stream().findFirst().orElse(new OrganisationDetails());
        persisted.setOrganisationName(organisationDetails.getOrganisationName());
        persisted.setToolName(organisationDetails.getToolName());
        organisationDetailsRepository.save(persisted);
        model.addAttribute("organisationDetails", persisted);
        model.addAttribute("saved", true);
        
        // Check if template exists
        if (persisted.getWordTemplatePath() != null && !persisted.getWordTemplatePath().isEmpty()) {
            File templateFile = new File(persisted.getWordTemplatePath());
            if (templateFile.exists()) {
                model.addAttribute("hasExistingTemplate", true);
                model.addAttribute("currentTemplateName", persisted.getWordTemplateFilename() != null ? persisted.getWordTemplateFilename() : "Unknown");
            } else {
                model.addAttribute("hasExistingTemplate", false);
            }
        } else {
            model.addAttribute("hasExistingTemplate", false);
        }
        
        return "organisation-details";
    }

    /**
     * Upload a Word template file
     */
    @PostMapping("/upload-template")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadTemplate(@RequestParam("wordTemplate") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Validate file
            if (file.isEmpty()) {
                response.put("success", false);
                response.put("message", "File is empty");
                return ResponseEntity.badRequest().body(response);
            }

            String filename = file.getOriginalFilename();
            if (filename == null || (!filename.endsWith(".docx") && !filename.endsWith(".doc"))) {
                response.put("success", false);
                response.put("message", "Invalid file type. Only .docx and .doc files are allowed");
                return ResponseEntity.badRequest().body(response);
            }

            // Create upload directory if it doesn't exist
            Path uploadDirPath = Paths.get(TEMPLATE_UPLOAD_DIR);
            Files.createDirectories(uploadDirPath);

            // Generate unique filename to avoid conflicts
            String uniqueFilename = UUID.randomUUID().toString() + "_" + filename;
            Path filePath = uploadDirPath.resolve(uniqueFilename);

            // Save file
            Files.copy(file.getInputStream(), filePath);

            // Update organisation details
            OrganisationDetails details = organisationDetailsRepository.findAll().stream().findFirst().orElse(new OrganisationDetails());
            
            // Delete old template if exists
            if (details.getWordTemplatePath() != null && !details.getWordTemplatePath().isEmpty()) {
                try {
                    File oldFile = new File(details.getWordTemplatePath());
                    if (oldFile.exists()) {
                        oldFile.delete();
                    }
                } catch (Exception e) {
                    System.err.println("Error deleting old template: " + e.getMessage());
                }
            }

            // Save new template path
            details.setWordTemplatePath(filePath.toAbsolutePath().toString());
            details.setWordTemplateFilename(filename);
            organisationDetailsRepository.save(details);

            // Analyze the template and persist findings to DB
            try {
                assessmentReporterWord.analyzeAndPersistTemplate(filePath.toAbsolutePath().toString());
            } catch (Exception e) {
                System.err.println("[OrganisationDetailsController] Template analysis failed (non-fatal): " + e.getMessage());
            }

            response.put("success", true);
            response.put("message", "Template uploaded successfully");
            response.put("templateName", filename);
            response.put("templatePath", filePath.toAbsolutePath().toString());

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            System.err.println("Error uploading template: " + e.getMessage());
            response.put("success", false);
            response.put("message", "Error uploading file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            response.put("success", false);
            response.put("message", "Unexpected error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Remove the Word template
     */
    @PostMapping("/remove-template")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> removeTemplate() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            OrganisationDetails details = organisationDetailsRepository.findAll().stream().findFirst().orElse(null);
            
            if (details == null || details.getWordTemplatePath() == null || details.getWordTemplatePath().isEmpty()) {
                response.put("success", false);
                response.put("message", "No template to remove");
                return ResponseEntity.badRequest().body(response);
            }

            // Delete file
            try {
                File templateFile = new File(details.getWordTemplatePath());
                if (templateFile.exists()) {
                    templateFile.delete();
                }
            } catch (Exception e) {
                System.err.println("Error deleting template file: " + e.getMessage());
            }

            // Update organisation details
            details.setWordTemplatePath(null);
            details.setWordTemplateFilename(null);
            details.setWordTemplateAnalysisJson(null);
            details.setWordTemplateChecksum(null);
            details.setWordTemplateStyleMappingJson(null);
            organisationDetailsRepository.save(details);

            response.put("success", true);
            response.put("message", "Template removed successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("Error removing template: " + e.getMessage());
            response.put("success", false);
            response.put("message", "Error removing template: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get the path to the currently stored Word template (if any)
     * This can be called by AssessmentReporter to retrieve the template path
     */
    @GetMapping("/template-path")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getTemplatePath() {
        Map<String, Object> response = new HashMap<>();
        
        OrganisationDetails details = organisationDetailsRepository.findAll().stream().findFirst().orElse(null);
        
        if (details != null && details.getWordTemplatePath() != null && !details.getWordTemplatePath().isEmpty()) {
            File templateFile = new File(details.getWordTemplatePath());
            if (templateFile.exists()) {
                response.put("exists", true);
                response.put("path", details.getWordTemplatePath());
                response.put("filename", details.getWordTemplateFilename());
                return ResponseEntity.ok(response);
            }
        }
        
        response.put("exists", false);
        response.put("path", null);
        response.put("filename", null);
        return ResponseEntity.ok(response);
    }

    /**
     * Return available styles from the analysed template and the current style mapping.
     */
    @GetMapping("/template-styles")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getTemplateStyles() {
        Map<String, Object> response = new HashMap<>();
        try {
            OrganisationDetails details = organisationDetailsRepository.findAll().stream().findFirst().orElse(null);
            if (details == null) {
                response.put("success", false);
                response.put("message", "No organisation configuration found.");
                return ResponseEntity.ok(response);
            }
            // Always re-analyse the template to pick up latest code changes (table styles, fuzzy placeholders, AI hints).
            // The AI call inside uses caching so repeated opens do not cause extra API calls.
            if (details.getWordTemplatePath() != null && !details.getWordTemplatePath().isBlank()) {
                File tplFile = new File(details.getWordTemplatePath());
                if (tplFile.exists()) {
                    assessmentReporterWord.analyzeAndPersistTemplate(details.getWordTemplatePath());
                    // Reload after analysis
                    details = organisationDetailsRepository.findAll().stream().findFirst().orElse(details);
                }
            }
            if (details.getWordTemplateAnalysisJson() == null) {
                response.put("success", false);
                response.put("message", "No template analysis available. Upload a template first.");
                return ResponseEntity.ok(response);
            }
            AssessmentReporterWord.WordTemplateMetadata meta =
                new ObjectMapper().readValue(details.getWordTemplateAnalysisJson(),
                    AssessmentReporterWord.WordTemplateMetadata.class);

            AssessmentReporterWord.WordStyleMapping mapping = new AssessmentReporterWord.WordStyleMapping();
            if (details.getWordTemplateStyleMappingJson() != null && !details.getWordTemplateStyleMappingJson().isBlank()) {
                mapping = new ObjectMapper().readValue(details.getWordTemplateStyleMappingJson(),
                    AssessmentReporterWord.WordStyleMapping.class);
            }

            response.put("success", true);
            response.put("availableStyles", meta.getAvailableStyles());
            response.put("tableStyles", meta.getTableStyles());
            response.put("tableStyleNames", meta.getTableStyleNames() != null ? meta.getTableStyleNames() : new java.util.HashMap<>());
            response.put("foundMarkers", meta.getFoundMarkers());
            response.put("hasHeader", meta.isHasHeader());
            response.put("headerText", meta.getHeaderText());
            response.put("hasFooter", meta.isHasFooter());
            response.put("footerText", meta.getFooterText());
            response.put("detectedPlaceholderTexts", meta.getDetectedPlaceholderTexts() != null ? meta.getDetectedPlaceholderTexts() : new java.util.HashMap<>());
            response.put("aiCandidates", meta.getAiCandidates() != null ? meta.getAiCandidates() : new java.util.LinkedHashMap<>());
            response.put("detectedPlaceholders", meta.getDetectedPlaceholders() != null ? meta.getDetectedPlaceholders() : new java.util.ArrayList<>());
            response.put("characterStyles", meta.getCharacterStyles() != null ? meta.getCharacterStyles() : new java.util.ArrayList<>());
            response.put("headingHierarchy", meta.getHeadingHierarchy() != null ? meta.getHeadingHierarchy() : new java.util.ArrayList<>());
            response.put("toc", meta.getToc());
            response.put("headerInfo", meta.getHeaderInfo());
            response.put("footerInfo", meta.getFooterInfo());
            response.put("mapping", mapping);
            // Placeholder attribute mapping (role → assessment attribute path)
            AssessmentReporterWord.WordPlaceholderAttributeMapping phMapping = new AssessmentReporterWord.WordPlaceholderAttributeMapping();
            if (details.getWordTemplatePlaceholderMappingJson() != null && !details.getWordTemplatePlaceholderMappingJson().isBlank()) {
                try {
                    phMapping = new ObjectMapper().readValue(details.getWordTemplatePlaceholderMappingJson(),
                            AssessmentReporterWord.WordPlaceholderAttributeMapping.class);
                } catch (Exception ignored) {}
            }
            response.put("placeholderMapping", phMapping.getRoleToAttribute());
            response.put("roleToSelectedSectionIndex", phMapping.getRoleToSelectedSectionIndex());
            response.put("customRoles", phMapping.getCustomRoles());
            response.put("structure", meta.getStructure() != null ? meta.getStructure() : new java.util.ArrayList<>());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error reading template analysis: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Save the user's style mapping (which template style to use for each report role).
     */
    @PostMapping("/save-style-mapping")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveStyleMapping(
            @RequestBody AssessmentReporterWord.WordStyleMapping mapping) {
        Map<String, Object> response = new HashMap<>();
        try {
            OrganisationDetails details = organisationDetailsRepository.findAll().stream().findFirst().orElse(null);
            if (details == null) {
                response.put("success", false);
                response.put("message", "No organisation configuration found");
                return ResponseEntity.badRequest().body(response);
            }
            String json = new ObjectMapper().writeValueAsString(mapping);
            details.setWordTemplateStyleMappingJson(json);
            organisationDetailsRepository.save(details);
            response.put("success", true);
            response.put("message", "Style mapping saved");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error saving style mapping: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Save the user's placeholder → assessment attribute mapping.
     */
    @PostMapping("/save-placeholder-mapping")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> savePlaceholderMapping(
            @RequestBody AssessmentReporterWord.WordPlaceholderAttributeMapping mapping) {
        Map<String, Object> response = new HashMap<>();
        try {
            OrganisationDetails details = organisationDetailsRepository.findAll().stream().findFirst().orElse(null);
            if (details == null) {
                response.put("success", false);
                response.put("message", "No organisation configuration found");
                return ResponseEntity.badRequest().body(response);
            }
            String json = new ObjectMapper().writeValueAsString(mapping);
            details.setWordTemplatePlaceholderMappingJson(json);
            organisationDetailsRepository.save(details);
            response.put("success", true);
            response.put("message", "Placeholder mapping saved");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error saving placeholder mapping: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
