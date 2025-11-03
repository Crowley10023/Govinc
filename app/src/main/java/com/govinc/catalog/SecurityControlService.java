package com.govinc.catalog;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.govinc.assessment.AssessmentControlAnswer;
import com.govinc.assessment.AssessmentControlAnswerRepository;

@Service
public class SecurityControlService {
    @Autowired
    private SecurityControlRepository repository;
    @Autowired
    private SecurityCatalogRepository securityCatalogRepository;
    @Autowired
    private AssessmentControlAnswerRepository assessmentControlAnswerRepository;

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
                // update fields
                existingControl.setName(control.getName());
                existingControl.setDetail(control.getDetail());
                existingControl.setReference(control.getReference());
                existingControl.setSecurityControlDomain(control.getSecurityControlDomain());
                return repository.save(existingControl);
            }
        }
        return repository.save(control);
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
