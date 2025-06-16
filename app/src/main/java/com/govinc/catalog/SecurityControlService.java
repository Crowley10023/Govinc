package com.govinc.catalog;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SecurityControlService {
    @Autowired
    private SecurityControlRepository repository;
    @Autowired
    private SecurityCatalogRepository securityCatalogRepository;

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
    public void deleteById(Long id) {
        Optional<SecurityControl> controlOpt = repository.findById(id);
        if (controlOpt.isPresent()) {
            SecurityControl control = controlOpt.get();
            // Remove security control from all catalogs that contain it
            for (SecurityCatalog catalog : control.getSecurityCatalogs()) {
                catalog.getSecurityControls().remove(control);
                securityCatalogRepository.save(catalog);
            }
            // Now it is safe to delete the control
            repository.deleteById(id);
        }
    }
}
