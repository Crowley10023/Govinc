package com.govinc.reporting;

import com.govinc.catalog.SecurityCapability;
import com.govinc.catalog.SecurityCatalog;
import com.govinc.maturity.MaturityModel;
import com.govinc.organization.OrgUnit;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "capability_reports")
public class CapabilityReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(length = 5000)
    private String description;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "security_catalog_id")
    private SecurityCatalog securityCatalog;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "org_unit_id")
    private OrgUnit orgUnit;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "maturity_model_id")
    private MaturityModel maturityModel;

    @ManyToMany
    @JoinTable(
        name = "capability_report_capabilities",
        joinColumns = @JoinColumn(name = "report_id"),
        inverseJoinColumns = @JoinColumn(name = "capability_id")
    )
    private List<SecurityCapability> capabilities = new ArrayList<>();

    public CapabilityReport() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public SecurityCatalog getSecurityCatalog() { return securityCatalog; }
    public void setSecurityCatalog(SecurityCatalog securityCatalog) { this.securityCatalog = securityCatalog; }

    public OrgUnit getOrgUnit() { return orgUnit; }
    public void setOrgUnit(OrgUnit orgUnit) { this.orgUnit = orgUnit; }

    public MaturityModel getMaturityModel() { return maturityModel; }
    public void setMaturityModel(MaturityModel maturityModel) { this.maturityModel = maturityModel; }

    public List<SecurityCapability> getCapabilities() { return capabilities; }
    public void setCapabilities(List<SecurityCapability> capabilities) { this.capabilities = capabilities; }
}
