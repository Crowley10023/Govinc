package com.govinc.controller;

import com.govinc.entity.OrganisationDetails;
import com.govinc.entity.OrganisationDetailsRepository;
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

@Controller
@RequestMapping("/config/organisation-details")
public class OrganisationDetailsController {
    @Autowired
    private OrganisationDetailsRepository organisationDetailsRepository;

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
}
