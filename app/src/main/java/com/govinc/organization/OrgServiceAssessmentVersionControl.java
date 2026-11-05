package com.govinc.organization;

import jakarta.persistence.*;

@Entity
@Table(name = "orgservice_assessment_version_controls")
public class OrgServiceAssessmentVersionControl {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", nullable = false)
    private OrgServiceAssessmentVersion version;

    @Column(nullable = false)
    private Long securityControlId;

    @Column(nullable = false)
    private boolean applicable;

    @Column(nullable = false)
    private int percent;

    @Column(columnDefinition = "TEXT")
    private String comment;

    public OrgServiceAssessmentVersionControl() {
    }

    public Long getId() { return id; }
    public OrgServiceAssessmentVersion getVersion() { return version; }
    public void setVersion(OrgServiceAssessmentVersion version) { this.version = version; }
    public Long getSecurityControlId() { return securityControlId; }
    public void setSecurityControlId(Long securityControlId) { this.securityControlId = securityControlId; }
    public boolean isApplicable() { return applicable; }
    public void setApplicable(boolean applicable) { this.applicable = applicable; }
    public int getPercent() { return percent; }
    public void setPercent(int percent) { this.percent = percent; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
