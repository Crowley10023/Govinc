package com.govinc.catalog;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class SecurityCapabilityService {

    @Autowired
    private SecurityCapabilityRepository repository;

    public List<SecurityCapability> findAll() { return repository.findAll(); }

    public Optional<SecurityCapability> findById(Long id) { return repository.findById(id); }

    public SecurityCapability save(SecurityCapability capability) { return repository.save(capability); }

    public void deleteById(Long id) { repository.deleteById(id); }

    @Transactional
    public void addDomainToCapability(Long capabilityId, SecurityControlDomain domain) {
        SecurityCapability cap = repository.findById(capabilityId)
                .orElseThrow(() -> new NoSuchElementException("Capability not found: " + capabilityId));
        cap.getDomains().add(domain);
        repository.save(cap);
    }

    @Transactional
    public void removeDomainFromCapability(Long capabilityId, Long domainId) {
        SecurityCapability cap = repository.findById(capabilityId)
                .orElseThrow(() -> new NoSuchElementException("Capability not found: " + capabilityId));
        cap.getDomains().removeIf(d -> d.getId().equals(domainId));
        repository.save(cap);
    }
}
