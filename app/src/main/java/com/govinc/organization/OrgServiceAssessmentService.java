package com.govinc.organization;

import com.govinc.catalog.SecurityControl;
import com.govinc.catalog.SecurityControlRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrgServiceAssessmentService {
    private final OrgServiceAssessmentRepository assessmentRepository;
    private final OrgServiceAssessmentControlRepository controlRepository;
    private final SecurityControlRepository securityControlRepository;
    private final OrgServiceRepository orgServiceRepository;
    private final OrgServiceService orgServiceService;
    private final OrgServiceAssessmentVersionRepository versionRepository;

    public OrgServiceAssessmentService(OrgServiceAssessmentRepository assessmentRepository,
                                       OrgServiceAssessmentControlRepository controlRepository,
                                       SecurityControlRepository securityControlRepository,
                                       OrgServiceRepository orgServiceRepository,
                                       OrgServiceService orgServiceService,
                                       OrgServiceAssessmentVersionRepository versionRepository) {
        this.versionRepository = versionRepository;
        this.assessmentRepository = assessmentRepository;
        this.controlRepository = controlRepository;
        this.securityControlRepository = securityControlRepository;
        this.orgServiceRepository = orgServiceRepository;
        this.orgServiceService = orgServiceService;
    }

    public OrgServiceAssessment findOrCreateAssessment(Long orgServiceId) {
        List<OrgServiceAssessment> existing = assessmentRepository.findByOrgServiceId(orgServiceId);
        if (!existing.isEmpty()) return existing.get(0);

        OrgService orgService = orgServiceRepository.findById(orgServiceId).orElseThrow();
        OrgServiceAssessment assessment = new OrgServiceAssessment(orgService, LocalDate.now());
        List<SecurityControl> allControls = securityControlRepository.findAll();
        List<OrgServiceAssessment> allAssessments = assessmentRepository.findAll();

        List<OrgServiceAssessmentControl> controls = new ArrayList<>();
        for (SecurityControl sc : allControls) {
            OrgServiceAssessmentControl control = new OrgServiceAssessmentControl();
            control.setSecurityControl(sc);
            control.setApplicable(false);
            control.setPercent(0);
            control.setOrgServiceAssessment(assessment);
            controls.add(control);
        }
        assessment.setControls(controls);
        assessmentRepository.save(assessment);
        return assessment;
    }

    /**
     * Given an OrgServiceAssessment, returns a list of controls where each control
     * is marked if it is already answered by another service and set with that name.
     */
    public List<OrgServiceAssessmentControl> enrichControlsWithLockInfo(OrgServiceAssessment assessment) {
        List<OrgServiceAssessment> allAssessments = assessmentRepository.findAll();
        Map<Long, String> controlIdToServiceName = new HashMap<>();
        for (OrgServiceAssessment a : allAssessments) {
            if (assessment.getId() != null && a.getId() != null && assessment.getId().equals(a.getId())) continue;
            if (a.getOrgService() != null && a.getControls() != null) {
                for (OrgServiceAssessmentControl c : a.getControls()) {
                    if (c.isApplicable() && !controlIdToServiceName.containsKey(c.getSecurityControl().getId())) {
                        controlIdToServiceName.put(c.getSecurityControl().getId(), a.getOrgService().getName());
                    }
                }
            }
        }
        List<OrgServiceAssessmentControl> result = new ArrayList<>();
        for (OrgServiceAssessmentControl control : assessment.getControls()) {
            if (controlIdToServiceName.containsKey(control.getSecurityControl().getId())) {
                control.setAnsweredByAnotherAssessment(true);
                control.setAnsweredByOrgServiceName(controlIdToServiceName.get(control.getSecurityControl().getId()));
            } else {
                control.setAnsweredByAnotherAssessment(false);
                control.setAnsweredByOrgServiceName(null);
            }
            result.add(control);
        }
        return result;
    }

    public List<OrgServiceAssessmentControl> getAllControlsForAssessment(OrgServiceAssessment assessment) {
        List<SecurityControl> allControls = securityControlRepository.findAll();
        Map<Long, OrgServiceAssessmentControl> existingMap = assessment.getControls().stream()
                .collect(Collectors.toMap(c -> c.getSecurityControl().getId(), c -> c));
        List<OrgServiceAssessmentControl> result = new ArrayList<>();
        for (SecurityControl sc : allControls) {
            OrgServiceAssessmentControl control = existingMap.getOrDefault(sc.getId(), null);
            if (control == null) {
                control = new OrgServiceAssessmentControl();
                control.setSecurityControl(sc);
                control.setApplicable(false);
                control.setPercent(0);
                control.setComment("");
                control.setOrgServiceAssessment(assessment);
            }
            result.add(control);
        }
        // Update lock info as existing method does
        OrgServiceAssessment temp = assessment;
        temp.setControls(result);
        return enrichControlsWithLockInfo(temp);
    }

    /** Display info for one version in the version selector. */
    public static class VersionInfo {
        private final int number;
        private final java.time.LocalDateTime createdAt;
        private final String createdBy;
        private final boolean current;

        public VersionInfo(int number, java.time.LocalDateTime createdAt, String createdBy, boolean current) {
            this.number = number;
            this.createdAt = createdAt;
            this.createdBy = createdBy;
            this.current = current;
        }

        public int getNumber() { return number; }
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
        public String getCreatedBy() { return createdBy; }
        public boolean isCurrent() { return current; }
    }

    /** All versions of the assessment, newest first; the current (live) version is always included. */
    @Transactional(readOnly = true)
    public List<VersionInfo> listVersions(OrgServiceAssessment assessment) {
        int current = assessment.getVersionNumber();
        List<VersionInfo> result = new ArrayList<>();
        result.add(new VersionInfo(current, null, null, true));
        List<OrgServiceAssessmentVersion> snapshots =
                versionRepository.findByAssessmentIdOrderByVersionNumberAsc(assessment.getId());
        for (int i = snapshots.size() - 1; i >= 0; i--) {
            OrgServiceAssessmentVersion v = snapshots.get(i);
            result.add(new VersionInfo(v.getVersionNumber(), v.getCreatedAt(), v.getCreatedBy(), false));
        }
        return result;
    }

    /** Freezes the current state as its own version and starts the next one (initially identical values). */
    @Transactional
    public int makeNewVersion(Long orgServiceId, String createdBy) {
        OrgServiceAssessment assessment = findOrCreateAssessment(orgServiceId);
        int current = assessment.getVersionNumber();

        OrgServiceAssessmentVersion snapshot = new OrgServiceAssessmentVersion();
        snapshot.setAssessment(assessment);
        snapshot.setVersionNumber(current);
        snapshot.setCreatedAt(java.time.LocalDateTime.now());
        snapshot.setCreatedBy(createdBy);
        for (OrgServiceAssessmentControl c : assessment.getControls()) {
            boolean hasComment = c.getComment() != null && !c.getComment().isBlank();
            if (!c.isApplicable() && c.getPercent() == 0 && !hasComment) continue;
            OrgServiceAssessmentVersionControl vc = new OrgServiceAssessmentVersionControl();
            vc.setVersion(snapshot);
            vc.setSecurityControlId(c.getSecurityControl().getId());
            vc.setApplicable(c.isApplicable());
            vc.setPercent(c.getPercent());
            vc.setComment(c.getComment());
            snapshot.getControls().add(vc);
        }
        versionRepository.save(snapshot);

        assessment.setVersionNumber(current + 1);
        assessmentRepository.save(assessment);
        return current + 1;
    }

    /** Controls as they were in a past version, or the live controls when the version is the current one. */
    @Transactional(readOnly = true)
    public List<OrgServiceAssessmentControl> getControlsForVersion(OrgServiceAssessment assessment, int version) {
        if (version >= assessment.getVersionNumber()) {
            return getAllControlsForAssessment(assessment);
        }
        Map<Long, OrgServiceAssessmentVersionControl> stored = snapshotControls(assessment, version);
        List<OrgServiceAssessmentControl> result = new ArrayList<>();
        for (SecurityControl sc : securityControlRepository.findAll()) {
            OrgServiceAssessmentVersionControl vc = stored.get(sc.getId());
            OrgServiceAssessmentControl c = new OrgServiceAssessmentControl(sc,
                    vc != null && vc.isApplicable(), vc != null ? vc.getPercent() : 0,
                    vc != null && vc.getComment() != null ? vc.getComment() : "");
            result.add(c);
        }
        return result;
    }

    /** Values of the version directly preceding the given one, keyed by security control id (empty for version 1). */
    @Transactional(readOnly = true)
    public Map<Long, OrgServiceAssessmentVersionControl> getPreviousValues(OrgServiceAssessment assessment, int version) {
        if (version <= 1) return Collections.emptyMap();
        return snapshotControls(assessment, version - 1);
    }

    /** Up to {@code limit} versions preceding {@code version}, newest first, as controlId -> [{version, percent, comment}]. */
    @Transactional(readOnly = true)
    public Map<Long, List<Map<String, Object>>> getHistory(OrgServiceAssessment assessment, int version, int limit) {
        Map<Long, List<Map<String, Object>>> result = new HashMap<>();
        int lowest = Math.max(1, version - limit);
        for (int v = version - 1; v >= lowest; v--) {
            Optional<OrgServiceAssessmentVersion> snap = versionRepository.findByAssessmentIdAndVersionNumber(assessment.getId(), v);
            if (snap.isEmpty()) continue;
            Map<Long, OrgServiceAssessmentVersionControl> stored = new HashMap<>();
            snap.get().getControls().forEach(c -> stored.put(c.getSecurityControlId(), c));
            for (SecurityControl sc : securityControlRepository.findAll()) {
                OrgServiceAssessmentVersionControl c = stored.get(sc.getId());
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("version", v);
                entry.put("percent", c != null ? c.getPercent() : 0);
                entry.put("comment", c != null && c.getComment() != null ? c.getComment() : "");
                result.computeIfAbsent(sc.getId(), k -> new ArrayList<>()).add(entry);
            }
        }
        return result;
    }

    private Map<Long, OrgServiceAssessmentVersionControl> snapshotControls(OrgServiceAssessment assessment, int version) {
        Map<Long, OrgServiceAssessmentVersionControl> map = new HashMap<>();
        versionRepository.findByAssessmentIdAndVersionNumber(assessment.getId(), version)
                .ifPresent(v -> v.getControls().forEach(c -> map.put(c.getSecurityControlId(), c)));
        return map;
    }

    public Optional<OrgServiceAssessment> getAssessment(Long assessmentId) {
        return assessmentRepository.findById(assessmentId);
    }

    public List<OrgServiceAssessment> getAllAssessments() {
        return assessmentRepository.findAll();
    }

    @Transactional
    public void saveAssessment(OrgServiceAssessment assessment) {
        saveAssessmentInternal(assessment, true);
    }

    @Transactional
    public void saveAssessmentWithoutValidation(OrgServiceAssessment assessment) {
        saveAssessmentInternal(assessment, false);
    }

    private void saveAssessmentInternal(OrgServiceAssessment assessment, boolean validateConstraints) {
        // Ensure each control correctly references its parent assessment
        // And ensure no duplicate maturity answer for a control per org service (only if validating)
        if (assessment.getOrgService() != null) {
            Long orgServiceId = assessment.getOrgService().getId();
            List<OrgServiceAssessment> allAssessments = assessmentRepository.findAll();
            for (OrgServiceAssessmentControl control : assessment.getControls()) {
                control.setOrgServiceAssessment(assessment);
                if (validateConstraints && control.isApplicable()) {
                    // Check if any assessment *in any service* already answered this control
                    for (OrgServiceAssessment otherA : allAssessments) {
                        if (assessment.getId() != null && assessment.getId().equals(otherA.getId())) continue;
                        if (otherA.getControls() != null) {
                            for (OrgServiceAssessmentControl otherC : otherA.getControls()) {
                                if (otherC.getSecurityControl().getId().equals(control.getSecurityControl().getId()) && otherC.isApplicable()) {
                                    throw new RuntimeException("This control is already answered by another service: "
                                            + (otherA.getOrgService() != null ? otherA.getOrgService().getName() : "unknown") + ".");
                                }
                            }
                        }
                    }
                }
            }
        }
        assessmentRepository.save(assessment);
    }
}
