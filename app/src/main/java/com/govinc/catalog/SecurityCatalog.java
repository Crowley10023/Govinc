package com.govinc.catalog;

import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;

import com.govinc.maturity.MaturityModel;

import jakarta.persistence.*;

@Entity
@Table(name = "security_catalogs")
public class SecurityCatalog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(length = 50000)
    private String description;
    private String revision;

    @ManyToMany
    @JoinTable(
        name = "security_catalog_controls",
        joinColumns = @JoinColumn(name = "security_catalog_id"),
        inverseJoinColumns = @JoinColumn(name = "security_control_id")
    )
    private Set<SecurityControl> securityControls = new HashSet<>();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "maturity_model_id")
    private MaturityModel maturityModel;

    public SecurityCatalog() {}

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
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public String getRevision() {
        return revision;
    }
    public void setRevision(String revision) {
        this.revision = revision;
    }
    /**
     * Returns security controls sorted by name.
     * @return a list of security controls sorted by name
     */
    public List<SecurityControl> getSecurityControls() {
        return securityControls.stream()
            .sorted(Comparator.comparing(SecurityControl::getName, String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());
    }
    public void setSecurityControls(Set<SecurityControl> securityControls) {
        this.securityControls = securityControls;
    }

    public MaturityModel getMaturityModel() {
        return maturityModel;
    }
    public void setMaturityModel(MaturityModel maturityModel) {
        this.maturityModel = maturityModel;
    }
}
