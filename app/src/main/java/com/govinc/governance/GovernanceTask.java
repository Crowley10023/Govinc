package com.govinc.governance;

import com.govinc.catalog.SecurityCatalog;
import com.govinc.catalog.SecurityControl;
import com.govinc.catalog.SecurityControlDomain;
import com.govinc.user.User;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "governance_tasks")
public class GovernanceTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(length = 5000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.IDENTIFIED;

    @ManyToOne(optional = true)
    @JoinColumn(name = "assigned_user_id")
    private User assignedUser;

    @ManyToOne(optional = true)
    @JoinColumn(name = "security_control_id")
    private SecurityControl securityControl;

    @ManyToOne(optional = true)
    @JoinColumn(name = "security_catalog_id")
    private SecurityCatalog securityCatalog;

    @ManyToOne(optional = true)
    @JoinColumn(name = "security_control_domain_id")
    private SecurityControlDomain securityControlDomain;

    @ManyToOne(optional = true)
    @JoinColumn(name = "project_id")
    private GovernanceProject project;

    private LocalDate createdDate;

    private LocalDate dueDate;

    @ManyToOne(optional = true)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    public GovernanceTask() {
        this.createdDate = LocalDate.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }

    public User getAssignedUser() { return assignedUser; }
    public void setAssignedUser(User assignedUser) { this.assignedUser = assignedUser; }

    public SecurityControl getSecurityControl() { return securityControl; }
    public void setSecurityControl(SecurityControl securityControl) { this.securityControl = securityControl; }

    public SecurityCatalog getSecurityCatalog() { return securityCatalog; }
    public void setSecurityCatalog(SecurityCatalog securityCatalog) { this.securityCatalog = securityCatalog; }

    public SecurityControlDomain getSecurityControlDomain() { return securityControlDomain; }
    public void setSecurityControlDomain(SecurityControlDomain securityControlDomain) { this.securityControlDomain = securityControlDomain; }

    public GovernanceProject getProject() { return project; }
    public void setProject(GovernanceProject project) { this.project = project; }

    public LocalDate getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDate createdDate) { this.createdDate = createdDate; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
}
