package com.govinc.catalog;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SecurityControlDomainService {
    @Autowired
    private SecurityControlDomainRepository repository;

    public List<SecurityControlDomain> findAll() {
        return repository.findAll();
    }

    public Optional<SecurityControlDomain> findById(Long id) {
        return repository.findById(id);
    }

    public SecurityControlDomain save(SecurityControlDomain domain) {
        return repository.save(domain);
    }

    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}
