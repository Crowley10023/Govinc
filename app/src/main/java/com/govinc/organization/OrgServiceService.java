package com.govinc.organization;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

@Service
public class OrgServiceService {
    @Autowired
    private OrgServiceRepository orgServiceRepository;
    @Autowired
    private OrgServiceAssessmentRepository orgServiceAssessmentRepository;
    @PersistenceContext
    private EntityManager entityManager;

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

    @Transactional
    public void deleteOrgService(Long id) {
        // Remove from join tables that reference OrgService to avoid constraint violation
        entityManager.createNativeQuery("DELETE FROM assessment_orgservice WHERE orgservice_id = :id")
            .setParameter("id", id).executeUpdate();
        orgServiceAssessmentRepository.findByOrgServiceId(id).forEach(orgServiceAssessmentRepository::delete);
        orgServiceRepository.deleteById(id);
    }
}
