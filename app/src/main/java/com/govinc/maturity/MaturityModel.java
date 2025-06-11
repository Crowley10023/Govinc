package com.govinc.maturity;

import jakarta.persistence.Transient;
import java.util.HashSet;
import java.util.Set;

import com.govinc.catalog.SecurityCatalog;

import jakarta.persistence.*;

@Entity
@Table(name = "maturity_models")
public class MaturityModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name = "";
    private String description = "";

    @OneToOne
    @JoinColumn(name = "security_catalog_id", referencedColumnName = "id")
    private SecurityCatalog securityCatalog;

    @Transient
    private Long securityCatalogId = null;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "maturity_model_answers",
        joinColumns = @JoinColumn(name = "maturity_model_id"),
        inverseJoinColumns = @JoinColumn(name = "maturity_answer_id")
    )
    private Set<MaturityAnswer> maturityAnswers = new HashSet<>();

    public MaturityModel() {}

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
        this.name = name != null ? name : "";
    }

    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description != null ? description : "";
    }

    public SecurityCatalog getSecurityCatalog() {
        return securityCatalog;
    }
    public void setSecurityCatalog(SecurityCatalog securityCatalog) {
        this.securityCatalog = securityCatalog;
    }

    public Long getSecurityCatalogId() {
        return securityCatalogId;
    }
    public void setSecurityCatalogId(Long securityCatalogId) {
        this.securityCatalogId = securityCatalogId;
    }

    public Set<MaturityAnswer> getMaturityAnswers() {
        return maturityAnswers;
    }
    public void setMaturityAnswers(Set<MaturityAnswer> maturityAnswers) {
        this.maturityAnswers = maturityAnswers;
    }
}
