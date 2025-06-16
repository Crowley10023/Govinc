package com.govinc.catalog;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.*;

// Import SecurityControlDomain

@Entity
@Table(name = "security_controls")
public class SecurityControl {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    @Column(length = 10000)
    private String detail;
    private String reference;

    @ManyToMany(mappedBy = "securityControls")
    private Set<SecurityCatalog> securityCatalogs = new HashSet<>();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "security_control_domain_id")
    private SecurityControlDomain securityControlDomain;

    public SecurityControl() {}

    public SecurityControl(String name, String detail, String reference) {
        this.name = name;
        this.detail = detail;
        this.reference = reference;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }
    
    public Set<SecurityCatalog> getSecurityCatalogs() {
        return securityCatalogs;
    }
    
    public void setSecurityCatalogs(Set<SecurityCatalog> securityCatalogs) {
        this.securityCatalogs = securityCatalogs;
    }

    public SecurityControlDomain getSecurityControlDomain() {
        return securityControlDomain;
    }
    public void setSecurityControlDomain(SecurityControlDomain securityControlDomain) {
        this.securityControlDomain = securityControlDomain;
    }
}
