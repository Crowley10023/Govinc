package com.govinc.governance;

import com.govinc.catalog.HistoricSecurityControl;
import com.govinc.catalog.SecurityControl;
import com.govinc.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "security_control_change_tracking")
public class SecurityControlChangeTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "governance_project_id")
    private GovernanceProject governanceProject;

    @ManyToOne(optional = false)
    @JoinColumn(name = "security_control_id")
    private SecurityControl securityControl;

    @ManyToOne(optional = false)
    @JoinColumn(name = "previous_version_id")
    private HistoricSecurityControl previousVersion;

    private String fromVersion;
    private String toVersion;

    private LocalDateTime changedAt;

    @ManyToOne(optional = true)
    @JoinColumn(name = "changed_by_id")
    private User changedBy;

    public SecurityControlChangeTracking() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public GovernanceProject getGovernanceProject() { return governanceProject; }
    public void setGovernanceProject(GovernanceProject governanceProject) { this.governanceProject = governanceProject; }

    public SecurityControl getSecurityControl() { return securityControl; }
    public void setSecurityControl(SecurityControl securityControl) { this.securityControl = securityControl; }

    public HistoricSecurityControl getPreviousVersion() { return previousVersion; }
    public void setPreviousVersion(HistoricSecurityControl previousVersion) { this.previousVersion = previousVersion; }

    public String getFromVersion() { return fromVersion; }
    public void setFromVersion(String fromVersion) { this.fromVersion = fromVersion; }

    public String getToVersion() { return toVersion; }
    public void setToVersion(String toVersion) { this.toVersion = toVersion; }

    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }

    public User getChangedBy() { return changedBy; }
    public void setChangedBy(User changedBy) { this.changedBy = changedBy; }
}
