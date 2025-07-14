package com.govinc.assessment;

import com.govinc.catalog.SecurityControl;
import com.govinc.maturity.MaturityAnswer;

import jakarta.persistence.*;

@Entity
@Table(name = "assessment_control_answer")
public class AssessmentControlAnswer {
    @Column(length = 4096)
    private String comment; // New field for comment

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional=false)
    private SecurityControl securityControl;

    @ManyToOne(optional=false)
    private MaturityAnswer maturityAnswer;

    public AssessmentControlAnswer() {}

    public AssessmentControlAnswer(SecurityControl securityControl, MaturityAnswer maturityAnswer) {
        this(securityControl, maturityAnswer, null);
    }

    public AssessmentControlAnswer(SecurityControl securityControl, MaturityAnswer maturityAnswer, String comment) {
        this.securityControl = securityControl;
        this.maturityAnswer = maturityAnswer;
        this.comment = comment;
    }

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }

    public SecurityControl getSecurityControl() {
        return securityControl;
    }
    public void setSecurityControl(SecurityControl securityControl) {
        this.securityControl = securityControl;
    }

    public MaturityAnswer getMaturityAnswer() {
        return maturityAnswer;
    }
    public void setMaturityAnswer(MaturityAnswer maturityAnswer) {
        this.maturityAnswer = maturityAnswer;
    }

    // Added to support SpEL/Thymeleaf property 'answer'
    public MaturityAnswer getAnswer() {
        return getMaturityAnswer();
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    /**
     * Returns the rating (score 0-100) of the maturity answer for this control (or 0 if missing)
     */
    public int getScore() {
        if (maturityAnswer != null) {
            return maturityAnswer.getRating();
        }
        return 0;
    }
}