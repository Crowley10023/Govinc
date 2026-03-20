package com.govinc.catalog;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "security_capabilities")
public class SecurityCapability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(length = 5000)
    private String description;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "security_catalog_id")
    private SecurityCatalog securityCatalog;

    @ManyToMany
    @JoinTable(
        name = "security_capability_domains",
        joinColumns = @JoinColumn(name = "capability_id"),
        inverseJoinColumns = @JoinColumn(name = "domain_id")
    )
    private Set<SecurityControlDomain> domains = new HashSet<>();

    public SecurityCapability() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public SecurityCatalog getSecurityCatalog() { return securityCatalog; }
    public void setSecurityCatalog(SecurityCatalog securityCatalog) { this.securityCatalog = securityCatalog; }

    public Set<SecurityControlDomain> getDomains() { return domains; }
    public void setDomains(Set<SecurityControlDomain> domains) { this.domains = domains; }
}
