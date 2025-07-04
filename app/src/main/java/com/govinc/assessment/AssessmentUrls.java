package com.govinc.assessment;

import jakarta.persistence.*;

@Entity
@Table(name = "assessments_urls")
public class AssessmentUrls {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String url;

    private String responsiblePerson;

    private int lifetime = 1; // in days, default is 1

    // Removed assessmentDetails link
    
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "assessment_id", nullable = false)
    private Assessment assessment;

    public AssessmentUrls() {
    }

    public AssessmentUrls(String url, String responsiblePerson, int lifetime) {
        this.url = url;
        this.responsiblePerson = responsiblePerson;
        this.lifetime = lifetime;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getResponsiblePerson() {
        return responsiblePerson;
    }

    public void setResponsiblePerson(String responsiblePerson) {
        this.responsiblePerson = responsiblePerson;
    }

    public int getLifetime() {
        return lifetime;
    }

    public void setLifetime(int lifetime) {
        this.lifetime = lifetime;
    }

    // No getter/setter for assessmentDetails anymore

    public Assessment getAssessment() {
        return assessment;
    }

    public void setAssessment(Assessment assessment) {
        this.assessment = assessment;
        if (assessment != null && assessment.getAssessmentUrls() != this) {
            assessment.setAssessmentUrls(this);
        }
    }
}
