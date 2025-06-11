package com.govinc.assessment;

import com.govinc.catalog.SecurityControl;
import com.govinc.maturity.MaturityAnswer;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "assessment_details")
public class AssessmentDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToMany
    @JoinTable(
        name = "assessment_assessmentdetails",
        joinColumns = @JoinColumn(name = "assessmentdetails_id"),
        inverseJoinColumns = @JoinColumn(name = "assessment_id")
    )
    private Set<Assessment> assessments = new HashSet<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "assessmentdetails_id")
    private Set<AssessmentControlAnswer> controlAnswers = new HashSet<>();

    @OneToMany(mappedBy = "assessmentDetails", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<AssessmentUrls> assessmentUrls = new HashSet<>();

    private LocalDate date;

    public AssessmentDetails() {}

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }

    public Set<Assessment> getAssessments() {
        return assessments;
    }
    public void setAssessments(Set<Assessment> assessments) {
        this.assessments = assessments;
    }

    public Set<AssessmentControlAnswer> getControlAnswers() {
        return controlAnswers;
    }

    public void setControlAnswers(Set<AssessmentControlAnswer> controlAnswers) {
        this.controlAnswers.clear();
        if (controlAnswers != null) {
            this.controlAnswers.addAll(controlAnswers);
        }
    }

    public Set<AssessmentUrls> getAssessmentUrls() {
        return assessmentUrls;
    }

    public void setAssessmentUrls(Set<AssessmentUrls> assessmentUrls) {
        this.assessmentUrls = assessmentUrls;
    }

    public LocalDate getDate() {
        return date;
    }
    public void setDate(LocalDate date) {
        this.date = date;
    }
}
