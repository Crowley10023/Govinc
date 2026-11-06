package com.govinc.assessment;

import com.govinc.catalog.SecurityControl;
import jakarta.persistence.*;

@Entity
@Table(name = "assessment_qualified_feedback", uniqueConstraints =
        @UniqueConstraint(columnNames = {"assessment_id", "security_control_id"}))
public class QualifiedFeedback {
    public enum Rating { MORE_DETAIL_NEEDED, REVISION_NEEDED, ACCEPTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "assessment_id", nullable = false)
    private Assessment assessment;

    @ManyToOne(optional = false)
    @JoinColumn(name = "security_control_id", nullable = false)
    private SecurityControl securityControl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rating rating;

    public QualifiedFeedback() {}
    public QualifiedFeedback(Assessment assessment, SecurityControl securityControl, Rating rating) {
        this.assessment = assessment;
        this.securityControl = securityControl;
        this.rating = rating;
    }
    public SecurityControl getSecurityControl() { return securityControl; }
    public Rating getRating() { return rating; }
    public void setRating(Rating rating) { this.rating = rating; }
}