package com.govinc.organization;

import com.govinc.catalog.SecurityControl;
import jakarta.persistence.*;

@Entity
@Table(name = "orgservice_assessment_controls")
public class OrgServiceAssessmentControl {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    private OrgServiceAssessment orgServiceAssessment;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "security_control_id", nullable = false)
    private SecurityControl securityControl;

    @Column(nullable = false)
    private boolean applicable;

    @Column(nullable = false)
    private int percent;

    public OrgServiceAssessmentControl() {}

    public OrgServiceAssessmentControl(SecurityControl securityControl, boolean applicable, int percent) {
        this.securityControl = securityControl;
        this.applicable = applicable;
        this.percent = percent;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public OrgServiceAssessment getOrgServiceAssessment() { return orgServiceAssessment; }
    public void setOrgServiceAssessment(OrgServiceAssessment orgServiceAssessment) { this.orgServiceAssessment = orgServiceAssessment; }
    public SecurityControl getSecurityControl() { return securityControl; }
    public void setSecurityControl(SecurityControl securityControl) { this.securityControl = securityControl; }
    public boolean isApplicable() { return applicable; }
    public void setApplicable(boolean applicable) { this.applicable = applicable; }
    public int getPercent() { return percent; }
    public void setPercent(int percent) { this.percent = percent; }
}
