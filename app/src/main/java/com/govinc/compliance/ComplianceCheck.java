package com.govinc.compliance;

import com.govinc.catalog.SecurityCatalog;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
public class ComplianceCheck {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;

    @ManyToOne(optional = false)
    private SecurityCatalog securityCatalog;

    @OneToMany(mappedBy = "complianceCheck", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ComplianceThreshold> thresholds = new ArrayList<>();

    // Optionally: Store lastCalculated values, reports, etc.

    // Getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public SecurityCatalog getSecurityCatalog() { return securityCatalog; }
    public void setSecurityCatalog(SecurityCatalog securityCatalog) { this.securityCatalog = securityCatalog; }
    public List<ComplianceThreshold> getThresholds() { return thresholds; }
    public void setThresholds(List<ComplianceThreshold> thresholds) { this.thresholds = thresholds; }
}