package com.govinc.catalog;

import java.util.List;
import java.util.Optional;
import java.util.HashSet;
import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityCatalogService {
    @Autowired
    private SecurityCatalogRepository repository;

    @Autowired
    private SecurityControlService securityControlService;

    @Autowired
    private com.govinc.assessment.AssessmentRepository assessmentRepository;

    public List<SecurityCatalog> findAll() {
        return repository.findAll();
    }

    public Optional<SecurityCatalog> findById(Long id) {
        return repository.findById(id);
    }

    public SecurityCatalog save(SecurityCatalog catalog) {
        if (catalog.getId() != null) {
            Optional<SecurityCatalog> existingOpt = repository.findById(catalog.getId());
            if (existingOpt.isPresent()) {
                SecurityCatalog existing = existingOpt.get();
                existing.setName(catalog.getName());
                existing.setDescription(catalog.getDescription());
                existing.setRevision(catalog.getRevision());
                existing.setSecurityControls(new HashSet<>(catalog.getSecurityControls()));
                // Fix: set maturity model, too
                existing.setMaturityModel(catalog.getMaturityModel());
                return repository.save(existing);
            }
        }
        // New entity, or not found by ID -- proceed as new
        return repository.save(catalog);
    }

    @Transactional
    public void deleteById(Long id) {
        SecurityCatalogDeletionResult result = deleteByIdWithResult(id, false);
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getMessage());
        }
    }

    @Transactional
    public void deleteById(Long id, boolean deleteAssociatedControls) {
        SecurityCatalogDeletionResult result = deleteByIdWithResult(id, deleteAssociatedControls);
        if (!result.isSuccess()) {
            throw new RuntimeException(result.getMessage());
        }
    }

    @Transactional
    public SecurityCatalogDeletionResult deleteByIdWithResult(Long id, boolean deleteAssociatedControls) {
        SecurityCatalogDeletionResult result = new SecurityCatalogDeletionResult();
        
        try {
            // Check if there are assessments using this catalog
            long assessmentCount = assessmentRepository.countBySecurityCatalogId(id);
            if (assessmentCount > 0) {
                result.setSuccess(false);
                result.setMessage(String.format(
                    "Cannot delete security catalog: %d assessment(s) are using this catalog. " +
                    "Please remove or reassign these assessments first.", assessmentCount)
                );
                return result;
            }

            Optional<SecurityCatalog> catalogOpt = repository.findById(id);
            if (!catalogOpt.isPresent()) {
                result.setSuccess(false);
                result.setMessage("Security catalog not found");
                return result;
            }
            
            SecurityCatalog catalog = catalogOpt.get();
            
            if (deleteAssociatedControls && !catalog.getSecurityControls().isEmpty()) {
                // Only delete controls that are ONLY used by this catalog
                for (SecurityControl control : catalog.getSecurityControls()) {
                    // Check if this control is used by other catalogs
                    long catalogCount = control.getSecurityCatalogs().size();
                    if (catalogCount <= 1) {
                        // Control is only used by this catalog, safe to delete
                        try {
                            securityControlService.deleteById(control.getId());
                            result.addControlDeleted(control.getName());
                        } catch (Exception e) {
                            result.addWarning("Could not delete security control '" + control.getName() + "': " + e.getMessage());
                            result.addControlSkipped(control.getName());
                        }
                    } else {
                        // Control is used by other catalogs, don't delete
                        result.addControlSkipped(control.getName());
                    }
                }
            } else {
                // Just clear the associations without deleting the controls
                catalog.getSecurityControls().clear();
                repository.save(catalog);
            }
            
            repository.deleteById(id);
            result.setSuccess(true);
            
            // Create appropriate success message
            if (deleteAssociatedControls) {
                if (result.getControlsSkipped().isEmpty() && result.getControlsDeleted().isEmpty()) {
                    result.setMessage("Security catalog deleted successfully (no controls were assigned)");
                } else if (result.getControlsSkipped().isEmpty()) {
                    result.setMessage("Security catalog and all associated controls deleted successfully");
                } else {
                    result.setMessage("Security catalog deleted successfully. Some controls were preserved because they are used by other catalogs");
                }
            } else {
                result.setMessage("Security catalog deleted successfully");
            }
            
        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage("Failed to delete security catalog: " + e.getMessage());
            result.addWarning("Unexpected error: " + e.getClass().getSimpleName());
        }
        
        return result;
    }
}