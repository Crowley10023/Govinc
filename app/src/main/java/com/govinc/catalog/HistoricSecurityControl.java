package com.govinc.catalog;

import com.govinc.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "historic_security_controls")
public class HistoricSecurityControl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "original_control_id")
    private SecurityControl originalControl;

    private String version;

    private String name;

    @Column(length = 10000)
    private String detail;

    private String reference;

    private String tag;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "security_control_domain_id")
    private SecurityControlDomain securityControlDomain;

    @ManyToOne(optional = true)
    @JoinColumn(name = "previous_version_id")
    private HistoricSecurityControl previousVersion;

    private LocalDateTime changedAt;

    @ManyToOne(optional = true)
    @JoinColumn(name = "changed_by_id")
    private User changedBy;

    public HistoricSecurityControl() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SecurityControl getOriginalControl() { return originalControl; }
    public void setOriginalControl(SecurityControl originalControl) { this.originalControl = originalControl; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    public SecurityControlDomain getSecurityControlDomain() { return securityControlDomain; }
    public void setSecurityControlDomain(SecurityControlDomain securityControlDomain) { this.securityControlDomain = securityControlDomain; }

    public HistoricSecurityControl getPreviousVersion() { return previousVersion; }
    public void setPreviousVersion(HistoricSecurityControl previousVersion) { this.previousVersion = previousVersion; }

    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }

    public User getChangedBy() { return changedBy; }
    public void setChangedBy(User changedBy) { this.changedBy = changedBy; }
}
