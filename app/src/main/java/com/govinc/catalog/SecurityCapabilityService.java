package com.govinc.catalog;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class SecurityCapabilityService {

    @Autowired
    private SecurityCapabilityRepository repository;

    public List<SecurityCapability> findAll() { return repository.findAll(); }

    public Optional<SecurityCapability> findById(Long id) { return repository.findById(id); }

    public SecurityCapability save(SecurityCapability capability) { return repository.save(capability); }

    public void deleteById(Long id) { repository.deleteById(id); }
}
