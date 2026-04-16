package com.govinc.organization;

import com.govinc.user.User;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
public class OrgService {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "orgservice_orgunit",
        joinColumns = @JoinColumn(name = "orgservice_id"),
        inverseJoinColumns = @JoinColumn(name = "orgunit_id")
    )
    private Set<OrgUnit> orgUnits;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "orgservice_responsible_persons",
        joinColumns = @JoinColumn(name = "orgservice_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> responsiblePersons = new HashSet<>();

    public OrgService() {}
    public OrgService(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Set<OrgUnit> getOrgUnits() { return orgUnits; }
    public void setOrgUnits(Set<OrgUnit> orgUnits) { this.orgUnits = orgUnits; }
    public Set<User> getResponsiblePersons() { return responsiblePersons; }
    public void setResponsiblePersons(Set<User> responsiblePersons) { this.responsiblePersons = responsiblePersons; }
}