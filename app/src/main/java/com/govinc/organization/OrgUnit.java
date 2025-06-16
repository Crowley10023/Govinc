package com.govinc.organization;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.govinc.user.User;
import jakarta.persistence.*;
import java.util.Set;

@Entity
public class OrgUnit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @OneToMany(fetch = FetchType.EAGER, mappedBy = "parent")
    @JsonManagedReference
    private Set<OrgUnit> children;

    @ManyToOne
    @JsonBackReference
    private OrgUnit parent;

    @OneToOne
    private User leader;

    public OrgUnit() {
    }

    public OrgUnit(String name, OrgUnit parent, User leader) {
        this.name = name;
        this.parent = parent;
        this.leader = leader;
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

    public Set<OrgUnit> getChildren() {
        return children;
    }

    public void setChildren(Set<OrgUnit> children) {
        this.children = children;
    }

    public OrgUnit getParent() {
        return parent;
    }

    public void setParent(OrgUnit parent) {
        this.parent = parent;
    }

    public User getLeader() {
        return leader;
    }

    public boolean isHasChildren() {
        return children != null && !children.isEmpty();
    }

    public void setLeader(User leader) {
        this.leader = leader;
    }
}
