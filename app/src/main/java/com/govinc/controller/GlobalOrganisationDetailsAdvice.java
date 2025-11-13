package com.govinc.controller;

import com.govinc.entity.OrganisationDetails;
import com.govinc.entity.OrganisationDetailsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.ui.Model;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ControllerAdvice
public class GlobalOrganisationDetailsAdvice {
    private static final Logger logger = LoggerFactory.getLogger(GlobalOrganisationDetailsAdvice.class);
    
    @Autowired
    private OrganisationDetailsRepository organisationDetailsRepository;
    
    @Value("${app.version.file:version.txt}")
    private String versionFile;

    @ModelAttribute
    public void addOrganisationDetails(Model model) {
        OrganisationDetails details = organisationDetailsRepository.findAll().stream().findFirst().orElse(new OrganisationDetails());
        model.addAttribute("organisationDetails", details);
        
        // Add app version
        String appVersion = getApplicationVersion();
        model.addAttribute("appVersion", appVersion);
    }
    
    private String getApplicationVersion() {
        try {
            // Try to read from version.txt
            String content = new String(Files.readAllBytes(Paths.get(versionFile))).trim();
            if (!content.isEmpty()) {
                return content;
            }
        } catch (Exception e) {
            logger.debug("Could not read version from file: " + e.getMessage());
        }
        return "1.0.0";
    }
}
