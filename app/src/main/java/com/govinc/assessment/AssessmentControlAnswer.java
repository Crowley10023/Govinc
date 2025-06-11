package com.govinc.assessment;

import com.govinc.catalog.SecurityControl;
import com.govinc.maturity.MaturityAnswer;

import jakarta.persistence.*;

@Entity
@Table(name = "assessment_control_answer")
public class AssessmentControlAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional=false)
    private SecurityControl securityControl;

    @ManyToOne(optional=false)
    private MaturityAnswer maturityAnswer;

    public AssessmentControlAnswer() {}

    public AssessmentControlAnswer(SecurityControl securityControl, MaturityAnswer maturityAnswer) {
        this.securityControl = securityControl;
        this.maturityAnswer = maturityAnswer;
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
}
