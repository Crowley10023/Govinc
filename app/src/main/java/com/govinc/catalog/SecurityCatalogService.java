package com.govinc.catalog;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SecurityCatalogService {
    @Autowired
    private SecurityCatalogRepository repository;

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
                existing.setSecurityControls(catalog.getSecurityControls());
                // Fix: set maturity model, too
                existing.setMaturityModel(catalog.getMaturityModel());
                return repository.save(existing);
            }
        }
        // New entity, or not found by ID -- proceed as new
        return repository.save(catalog);
    }

    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}
