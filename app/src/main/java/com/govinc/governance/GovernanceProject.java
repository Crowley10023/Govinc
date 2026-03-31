package com.govinc.governance;

import com.govinc.assessment.Assessment;
import com.govinc.user.User;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "governance_projects")
public class GovernanceProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(length = 5000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_type", nullable = false)
    private ProjectType projectType = ProjectType.OTHER;

    @ManyToOne(optional = true)
    @JoinColumn(name = "owner_id")
    private User owner;

    @OneToMany(mappedBy = "project", fetch = FetchType.EAGER)
    private List<GovernanceTask> tasks = new ArrayList<>();

    private LocalDate createdDate;

    @ManyToOne(optional = true)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    @Column(nullable = false)
    private boolean trackChanges = false;

    @ManyToMany
    @JoinTable(name = "project_linked_assessments",
               joinColumns = @JoinColumn(name = "project_id"),
               inverseJoinColumns = @JoinColumn(name = "assessment_id"))
    private Set<Assessment> linkedAssessments = new HashSet<>();

    public GovernanceProject() {
        this.createdDate = LocalDate.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public ProjectType getProjectType() { return projectType; }
    public void setProjectType(ProjectType projectType) { this.projectType = projectType; }

    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }

    public List<GovernanceTask> getTasks() { return tasks; }
    public void setTasks(List<GovernanceTask> tasks) { this.tasks = tasks; }

    public LocalDate getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDate createdDate) { this.createdDate = createdDate; }

    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }

    public boolean isTrackChanges() { return trackChanges; }
    public void setTrackChanges(boolean trackChanges) { this.trackChanges = trackChanges; }

    public Set<Assessment> getLinkedAssessments() { return linkedAssessments; }
    public void setLinkedAssessments(Set<Assessment> linkedAssessments) { this.linkedAssessments = linkedAssessments; }

    public int getTaskCount() { return tasks != null ? tasks.size() : 0; }

    public long getCompletedTaskCount() {
        if (tasks == null) return 0;
        return tasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
    }

    public int getLinkedAssessmentCount() { return linkedAssessments != null ? linkedAssessments.size() : 0; }
}
