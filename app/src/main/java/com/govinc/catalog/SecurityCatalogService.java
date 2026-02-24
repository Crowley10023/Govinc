package com.govinc.catalog;

import java.util.List;
import java.util.Optional;
import java.util.HashSet;
import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
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
                existing.setReportInstructions(catalog.getReportInstructions());
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
                // Create a copy of controls to avoid modification during iteration
                List<SecurityControl> controlsToProcess = new ArrayList<>(catalog.getSecurityControls());
                
                // Only delete controls that are ONLY used by this catalog
                for (SecurityControl control : controlsToProcess) {
                    // Check if this control is used by other catalogs (excluding current catalog)
                    long otherCatalogCount = control.getSecurityCatalogs().stream()
                        .filter(cat -> !cat.getId().equals(id))
                        .count();
                    
                    if (otherCatalogCount == 0) {
                        // Control is only used by this catalog, safe to delete
                        try {
                            // First remove from current catalog to avoid constraint issues
                            catalog.getSecurityControls().remove(control);
                            control.getSecurityCatalogs().remove(catalog);
                            
                            securityControlService.deleteById(control.getId());
                            result.addControlDeleted(control.getName());
                        } catch (DataIntegrityViolationException e) {
                            result.addWarning("Could not delete security control '" + control.getName() + 
                                "': Control is referenced by other entities (assessments, etc.). Skipping deletion.");
                            result.addControlSkipped(control.getName());
                        } catch (Exception e) {
                            result.addWarning("Could not delete security control '" + control.getName() + "': " + e.getMessage());
                            result.addControlSkipped(control.getName());
                        }
                    } else {
                        // Control is used by other catalogs, don't delete - just remove association
                        catalog.getSecurityControls().remove(control);
                        control.getSecurityCatalogs().remove(catalog);
                        result.addControlSkipped(control.getName());
                    }
                }
                
                // Save catalog with updated associations
                repository.save(catalog);
            } else {
                // Just clear the associations without deleting the controls
                if (!catalog.getSecurityControls().isEmpty()) {
                    // Properly remove bidirectional associations
                    List<SecurityControl> controlsToRemove = new ArrayList<>(catalog.getSecurityControls());
                    for (SecurityControl control : controlsToRemove) {
                        catalog.getSecurityControls().remove(control);
                        control.getSecurityCatalogs().remove(catalog);
                    }
                    repository.save(catalog);
                }
            }
            
            // Now delete the catalog
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
            
        } catch (DataIntegrityViolationException e) {
            result.setSuccess(false);
            String detailedError = "Cannot delete security catalog due to database constraints. ";
            if (e.getMessage().toLowerCase().contains("foreign key") || e.getMessage().toLowerCase().contains("constraint")) {
                detailedError += "This catalog is still referenced by other entities (assessments, controls, etc.). " +
                               "Please remove or reassign these references first.";
            } else {
                detailedError += "Database constraint violation: " + e.getMessage();
            }
            result.setMessage(detailedError);
        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage("Failed to delete security catalog: " + e.getMessage());
            result.addWarning("Unexpected error: " + e.getClass().getSimpleName());
        }
        
        return result;
    }
}