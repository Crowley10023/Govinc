package com.govinc.catalog;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.govinc.assessment.AssessmentControlAnswer;
import com.govinc.assessment.AssessmentControlAnswerRepository;
import com.govinc.governance.GovernanceProject;
import com.govinc.governance.GovernanceProjectRepository;
import com.govinc.governance.SecurityControlChangeTracking;
import com.govinc.governance.SecurityControlChangeTrackingRepository;
import com.govinc.user.User;

@Service
public class SecurityControlService {
    @Autowired
    private SecurityControlRepository repository;
    @Autowired
    private SecurityCatalogRepository securityCatalogRepository;
    @Autowired
    private AssessmentControlAnswerRepository assessmentControlAnswerRepository;
    @Autowired
    private HistoricSecurityControlRepository historicRepository;
    @Autowired
    private GovernanceProjectRepository governanceProjectRepository;
    @Autowired
    private SecurityControlChangeTrackingRepository changeTrackingRepository;

    public List<SecurityControl> findAll() {
        return repository.findAll();
    }

    public Optional<SecurityControl> findById(Long id) {
        return repository.findById(id);
    }

    public SecurityControl save(SecurityControl control) {
        if (control.getId() != null) {
            Optional<SecurityControl> existingControlOpt = repository.findById(control.getId());
            if (existingControlOpt.isPresent()) {
                SecurityControl existingControl = existingControlOpt.get();
                existingControl.setName(control.getName());
                existingControl.setDetail(control.getDetail());
                existingControl.setReference(control.getReference());
                existingControl.setTag(control.getTag());
                existingControl.setSecurityControlDomain(control.getSecurityControlDomain());
                return repository.save(existingControl);
            }
        }
        return repository.save(control);
    }

    @Transactional
    public SecurityControl saveWithVersioning(SecurityControl control, String versionBump, Long projectId, User changedBy) {
        if (control.getId() == null) {
            control.setVersion("1.0");
            return repository.save(control);
        }

        Optional<SecurityControl> existingOpt = repository.findById(control.getId());
        if (existingOpt.isEmpty()) {
            control.setVersion("1.0");
            return repository.save(control);
        }

        SecurityControl existing = existingOpt.get();
        String oldVersion = existing.getVersion() != null ? existing.getVersion() : "1.0";

        // Create historic snapshot of the current state before updating
        HistoricSecurityControl historic = new HistoricSecurityControl();
        historic.setOriginalControl(existing);
        historic.setVersion(oldVersion);
        historic.setName(existing.getName());
        historic.setDetail(existing.getDetail());
        historic.setReference(existing.getReference());
        historic.setTag(existing.getTag());
        historic.setSecurityControlDomain(existing.getSecurityControlDomain());
        historic.setChangedAt(LocalDateTime.now());
        historic.setChangedBy(changedBy);

        // Link to the previous historic version if one exists
        HistoricSecurityControl previousHistoric = historicRepository.findTopByOriginalControlIdOrderByChangedAtDesc(existing.getId());
        if (previousHistoric != null) {
            historic.setPreviousVersion(previousHistoric);
        }

        historicRepository.save(historic);

        // Compute new version
        String newVersion = bumpVersion(oldVersion, versionBump);

        // Update the active control
        existing.setName(control.getName());
        existing.setDetail(control.getDetail());
        existing.setReference(control.getReference());
        existing.setTag(control.getTag());
        existing.setSecurityControlDomain(control.getSecurityControlDomain());
        existing.setVersion(newVersion);
        SecurityControl saved = repository.save(existing);

        // Track change if within a project context
        if (projectId != null) {
            Optional<GovernanceProject> projectOpt = governanceProjectRepository.findById(projectId);
            if (projectOpt.isPresent()) {
                SecurityControlChangeTracking tracking = new SecurityControlChangeTracking();
                tracking.setGovernanceProject(projectOpt.get());
                tracking.setSecurityControl(saved);
                tracking.setPreviousVersion(historic);
                tracking.setFromVersion(oldVersion);
                tracking.setToVersion(newVersion);
                tracking.setChangedAt(LocalDateTime.now());
                tracking.setChangedBy(changedBy);
                changeTrackingRepository.save(tracking);
            }
        }

        return saved;
    }

    private String bumpVersion(String currentVersion, String bumpType) {
        if (currentVersion == null || currentVersion.isEmpty()) {
            currentVersion = "1.0";
        }
        String[] parts = currentVersion.split("\\.");
        int major = 1;
        int minor = 0;
        try {
            major = Integer.parseInt(parts[0]);
            if (parts.length > 1) {
                minor = Integer.parseInt(parts[1]);
            }
        } catch (NumberFormatException e) {
            major = 1;
            minor = 0;
        }

        if ("major".equalsIgnoreCase(bumpType)) {
            major++;
            minor = 0;
        } else {
            minor++;
        }
        return major + "." + minor;
    }

    public List<HistoricSecurityControl> getVersionHistory(Long controlId) {
        return historicRepository.findByOriginalControlIdOrderByChangedAtDesc(controlId);
    }

    // Updated delete method to remove from catalog associations first
    public void deleteById(Long id) throws SecurityControlInUseException {
        Optional<SecurityControl> controlOpt = repository.findById(id);
        if (controlOpt.isPresent()) {
            SecurityControl control = controlOpt.get();
            
            // Check if this control is used in any assessment
            List<AssessmentControlAnswer> answers = assessmentControlAnswerRepository.findAll().stream()
                .filter(a -> a.getSecurityControl() != null && a.getSecurityControl().getId().equals(id))
                .toList();
            
            if (!answers.isEmpty()) {
                throw new SecurityControlInUseException(
                    "Cannot delete security control '" + control.getName() + "'. It is currently used in " + 
                    answers.size() + " assessment(s). Please remove it from all assessments first."
                );
            }
            
            // Remove security control from all catalogs that contain it
            for (SecurityCatalog catalog : control.getSecurityCatalogs()) {
                catalog.getSecurityControls().remove(control);
                securityCatalogRepository.save(catalog);
            }
            // Now it is safe to delete the control
            try {
                repository.deleteById(id);
            } catch (Exception e) {
                // Check if it's a foreign key constraint error
                String errorMessage = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                if (errorMessage.contains("foreign key constraint") || errorMessage.contains("constraint")) {
                    throw new SecurityControlInUseException(
                        "Cannot delete security control '" + control.getName() + "'. " +
                        "It is currently referenced in the system and cannot be removed. " +
                        "Please ensure it is not used in any catalogs or assessments."
                    );
                }
                // Re-throw other exceptions
                throw e;
            }
        }
    }
    
    /**
     * Custom exception for when a security control cannot be deleted
     */
    public static class SecurityControlInUseException extends Exception {
        public SecurityControlInUseException(String message) {
            super(message);
        }
        
        public SecurityControlInUseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
