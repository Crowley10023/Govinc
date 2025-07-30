package com.govinc.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "organisation_details")
public class OrganisationDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String organisationName;

    @Column(nullable = false)
    private String toolName;

    public OrganisationDetails() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrganisationName() { return organisationName; }
    public void setOrganisationName(String organisationName) { this.organisationName = organisationName; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
}
