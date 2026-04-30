package com.govinc.assessment;

import com.govinc.catalog.SecurityCatalog;
import jakarta.persistence.*;

@Entity
@Table(name = "assessment_stereotypes")
public class AssessmentStereotype {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @ManyToOne(optional = true)
    @JoinColumn(name = "security_catalog_id")
    private SecurityCatalog securityCatalog;

    public AssessmentStereotype() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public SecurityCatalog getSecurityCatalog() { return securityCatalog; }
    public void setSecurityCatalog(SecurityCatalog securityCatalog) { this.securityCatalog = securityCatalog; }
}
