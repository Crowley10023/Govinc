package com.govinc.governance;

import com.govinc.user.User;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "governance_projects")
public class GovernanceProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(length = 5000)
    private String description;

    @ManyToOne(optional = true)
    @JoinColumn(name = "owner_id")
    private User owner;

    @OneToMany(mappedBy = "project", fetch = FetchType.EAGER)
    private List<GovernanceTask> tasks = new ArrayList<>();

    private LocalDate createdDate;

    @ManyToOne(optional = true)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

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

    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }

    public List<GovernanceTask> getTasks() { return tasks; }
    public void setTasks(List<GovernanceTask> tasks) { this.tasks = tasks; }

    public LocalDate getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDate createdDate) { this.createdDate = createdDate; }

    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }

    public int getTaskCount() { return tasks != null ? tasks.size() : 0; }

    public long getCompletedTaskCount() {
        if (tasks == null) return 0;
        return tasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
    }
}
