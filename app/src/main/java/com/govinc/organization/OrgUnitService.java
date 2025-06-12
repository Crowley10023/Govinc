package com.govinc.organization;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class OrgUnitService {
    @Autowired
    private OrgUnitRepository orgUnitRepository;

    public List<OrgUnit> getAllOrgUnits() {
        return orgUnitRepository.findAll();
    }

    public Optional<OrgUnit> getOrgUnit(Long id) {
        return orgUnitRepository.findById(id);
    }

    public OrgUnit addOrgUnit(OrgUnit orgUnit) {
        return orgUnitRepository.save(orgUnit);
    }

    public void deleteOrgUnit(Long id) {
        orgUnitRepository.deleteById(id);
    }
}
