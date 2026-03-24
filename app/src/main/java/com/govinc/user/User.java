package com.govinc.user;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.govinc.organization.OrgUnit;
import java.util.HashSet;
import java.util.Set;

@Entity
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String firstName;
    private String lastName;
    private String email;
    
    @Enumerated(EnumType.STRING)
    private Role role = Role.ASSESSMENT_DELEGATE;
    
    @OneToMany(mappedBy = "leader", fetch = FetchType.EAGER)
    @JsonBackReference
    private Set<OrgUnit> leadsOrgUnits = new HashSet<>();

    public User() {}

    public User(String firstName, String lastName, String email) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.role = Role.ASSESSMENT_DELEGATE;
    }
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the display name (firstName + lastName).
     * Used by templates via ${user.name} and JSON serialization for backward compatibility.
     */
    public String getName() {
        String fn = firstName != null ? firstName.trim() : "";
        String ln = lastName != null ? lastName.trim() : "";
        return ln.isEmpty() ? fn : (fn + " " + ln).trim();
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
    
    public Role getRole() {
        return role;
    }
    
    public void setRole(Role role) {
        this.role = role;
    }
    
    public Set<OrgUnit> getLeadsOrgUnits() {
        return leadsOrgUnits;
    }
    
    public void setLeadsOrgUnits(Set<OrgUnit> leadsOrgUnits) {
        this.leadsOrgUnits = leadsOrgUnits;
    }
    
    public void addLeadsOrgUnit(OrgUnit orgUnit) {
        if (this.leadsOrgUnits == null) {
            this.leadsOrgUnits = new HashSet<>();
        }
        this.leadsOrgUnits.add(orgUnit);
    }
    
    public void removeLeadsOrgUnit(OrgUnit orgUnit) {
        if (this.leadsOrgUnits != null) {
            this.leadsOrgUnits.remove(orgUnit);
        }
    }
}
