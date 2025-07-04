package com.govinc.organization;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class OrgServiceService {
    @Autowired
    private OrgServiceRepository orgServiceRepository;

    // DEMO seeding of org services if none are present
    @jakarta.annotation.PostConstruct
    public void initDemoServices() {
        if (orgServiceRepository.count() == 0) {
            orgServiceRepository.save(new OrgService("Demo OrgService A", "For testing"));
            orgServiceRepository.save(new OrgService("Demo OrgService B", "For testing"));
        }
    }

    public List<OrgService> getAllOrgServices() {
        return orgServiceRepository.findAll();
    }

    public Optional<OrgService> getOrgService(Long id) {
        return orgServiceRepository.findById(id);
    }

    public OrgService saveOrgService(OrgService orgService) {
        return orgServiceRepository.save(orgService);
    }

    public void deleteOrgService(Long id) {
        orgServiceRepository.deleteById(id);
    }
}