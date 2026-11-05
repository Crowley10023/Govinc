package com.govinc.organization;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Frozen snapshot (rating + comment per control) of an org service assessment at the time a new version was made. */
@Entity
@Table(name = "orgservice_assessment_versions")
public class OrgServiceAssessmentVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    private OrgServiceAssessment assessment;

    @Column(nullable = false)
    private int versionNumber;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private String createdBy;

    @OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrgServiceAssessmentVersionControl> controls = new ArrayList<>();

    public OrgServiceAssessmentVersion() {
    }

    public Long getId() { return id; }
    public OrgServiceAssessment getAssessment() { return assessment; }
    public void setAssessment(OrgServiceAssessment assessment) { this.assessment = assessment; }
    public int getVersionNumber() { return versionNumber; }
    public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public List<OrgServiceAssessmentVersionControl> getControls() { return controls; }
    public void setControls(List<OrgServiceAssessmentVersionControl> controls) { this.controls = controls; }
}
