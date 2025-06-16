package com.govinc.catalog;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "security_control_domains")
public class SecurityControlDomain {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    
    @Column(length = 5000)
    private String description;

    @OneToMany(mappedBy = "securityControlDomain")
    private Set<SecurityControl> securityControls = new HashSet<>();

    public SecurityControlDomain() {}

    public SecurityControlDomain(String name, String description) {
        this.name = name;
        this.description = description;
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
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public Set<SecurityControl> getSecurityControls() {
        return securityControls;
    }
    public void setSecurityControls(Set<SecurityControl> securityControls) {
        this.securityControls = securityControls;
    }
}
