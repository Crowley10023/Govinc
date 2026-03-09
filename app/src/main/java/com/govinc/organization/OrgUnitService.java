package com.govinc.organization;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

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

    /**
     * Recursively loads org unit with all children for all tree levels.
     * This ensures the entire org tree is fully fetched and ready for tree view.
     * Ensures that leader information is loaded for all nodes.
     */
    public Optional<OrgUnit> getOrgUnitWithChildrenRecursive(Long id) {
        Optional<OrgUnit> baseUnitOpt = orgUnitRepository.findByIdWithChildren(id);
        baseUnitOpt.ifPresent(unit -> {
            // Ensure the root unit has its leader loaded
            if (unit.getLeader() == null) {
                Optional<OrgUnit> withLeader = orgUnitRepository.findByIdWithLeader(id);
                if (withLeader.isPresent() && withLeader.get().getLeader() != null) {
                    unit.setLeader(withLeader.get().getLeader());
                }
            }
            fetchAllChildrenRecursive(unit, new HashSet<>());
        });
        return baseUnitOpt;
    }

    private void fetchAllChildrenRecursive(OrgUnit orgUnit, Set<Long> visited) {
        if (orgUnit == null || orgUnit.getId() == null) return;
        if (!visited.add(orgUnit.getId())) return; // already visited
        Set<OrgUnit> children = orgUnit.getChildren();
        if (children != null && !children.isEmpty()) {
            Set<OrgUnit> fullyLoadedChildren = new HashSet<>();
            for (OrgUnit child : children) {
                // Fetch each child with its own children AND leader
                Optional<OrgUnit> loadedChildOpt = orgUnitRepository.findByIdWithChildren(child.getId());
                if (loadedChildOpt.isPresent()) {
                    OrgUnit loadedChild = loadedChildOpt.get();
                    
                    // Ensure leader is loaded for this child
                    if (loadedChild.getLeader() == null) {
                        Optional<OrgUnit> childWithLeader = orgUnitRepository.findByIdWithLeader(child.getId());
                        if (childWithLeader.isPresent() && childWithLeader.get().getLeader() != null) {
                            loadedChild.setLeader(childWithLeader.get().getLeader());
                        }
                    }
                    
                    fullyLoadedChildren.add(loadedChild);
                    // Recursively fetch descendants
                    fetchAllChildrenRecursive(loadedChild, visited);
                }
            }
            orgUnit.setChildren(fullyLoadedChildren); // replace
        }
    }

    /**
     * Returns the direct children for a given parent org unit ID
     */
    public List<OrgUnit> getChildrenOfOrgUnit(Long parentId) {
        return orgUnitRepository.findByParentId(parentId);
    }

    /**
     * Returns all top-level org units (without parent), each fully loaded with descendants.
     */
    public List<OrgUnit> getTopLevelOrgUnitsWithChildrenRecursive() {
        List<OrgUnit> roots = orgUnitRepository.findByParentIsNullOrderByNameAsc();
        List<OrgUnit> loadedRoots = new ArrayList<>();
        for (OrgUnit root : roots) {
            Optional<OrgUnit> loaded = getOrgUnitWithChildrenRecursive(root.getId());
            loaded.ifPresent(loadedRoots::add);
        }
        return loadedRoots;
    }
}
