package com.govinc.organization;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orgservice_assessments")
public class OrgServiceAssessment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "orgservice_id", nullable = false)
    private OrgService orgService;

    @Column(nullable = false)
    private LocalDate assessmentDate;

    @OneToMany(mappedBy = "orgServiceAssessment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrgServiceAssessmentControl> controls = new ArrayList<>();

    public OrgServiceAssessment() {}

    public OrgServiceAssessment(OrgService orgService, LocalDate assessmentDate) {
        this.orgService = orgService;
        this.assessmentDate = assessmentDate;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public OrgService getOrgService() { return orgService; }
    public void setOrgService(OrgService orgService) { this.orgService = orgService; }
    public LocalDate getAssessmentDate() { return assessmentDate; }
    public void setAssessmentDate(LocalDate assessmentDate) { this.assessmentDate = assessmentDate; }
    public List<OrgServiceAssessmentControl> getControls() { return controls; }
    public void setControls(List<OrgServiceAssessmentControl> controls) { this.controls = controls; }
}
