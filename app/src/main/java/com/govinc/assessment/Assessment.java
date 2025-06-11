package com.govinc.assessment;

import java.time.LocalDate;
import com.govinc.catalog.SecurityCatalog;
import com.govinc.user.User;
import java.util.Set;
import java.util.HashSet;
import jakarta.persistence.*;

@Entity
@Table(name = "assessments")
public class Assessment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "security_catalog_id")
    private SecurityCatalog securityCatalog;

    private LocalDate date;
    
    private String name;

    @Enumerated(EnumType.STRING)
    private AssessmentStatus status = AssessmentStatus.OPEN;

    // Removed assessmentUrl field

    @OneToOne(mappedBy = "assessment", cascade = CascadeType.ALL)
    private AssessmentUrls assessmentUrls;
    
    // NEW: Predecessor assessment reference
    @ManyToOne
    @JoinColumn(name = "predecessor_id")
    private Assessment predecessor;

    public Assessment() {
        this.status = AssessmentStatus.OPEN;
    }

    public Assessment(SecurityCatalog securityCatalog, LocalDate date, String name, AssessmentStatus status) {
        this.securityCatalog = securityCatalog;
        this.date = date;
        this.name = name;
        this.status = status != null ? status : AssessmentStatus.OPEN;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SecurityCatalog getSecurityCatalog() {
        return securityCatalog;
    }

    public void setSecurityCatalog(SecurityCatalog securityCatalog) {
        this.securityCatalog = securityCatalog;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AssessmentStatus getStatus() {
        if (status == null) {
            status = AssessmentStatus.OPEN;
        }
        return status;
    }

    public void setStatus(AssessmentStatus status) {
        this.status = status;
    }

    public boolean isClosed() {
        return AssessmentStatus.CLOSED.equals(this.status);
    }

    public boolean isOpen() {
        return AssessmentStatus.OPEN.equals(this.status);
    }
    
    // Removed getAssessmentUrl and setAssessmentUrl methods
    
    public AssessmentUrls getAssessmentUrls() {
        return assessmentUrls;
    }

    public void setAssessmentUrls(AssessmentUrls assessmentUrls) {
        this.assessmentUrls = assessmentUrls;
    }

    @ManyToMany
    @JoinTable(
        name = "assessment_users",
        joinColumns = @JoinColumn(name = "assessment_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> users = new HashSet<>();

    public Set<User> getUsers() {
        return users;
    }

    public void setUsers(Set<User> users) {
        this.users = users;
    }

    // NEW: Getter and Setter for predecessor
    public Assessment getPredecessor() {
        return predecessor;
    }

    public void setPredecessor(Assessment predecessor) {
        this.predecessor = predecessor;
    }
}
