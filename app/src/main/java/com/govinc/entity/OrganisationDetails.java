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

    @Column(length = 500)
    private String wordTemplatePath;

    @Column(length = 255)
    private String wordTemplateFilename;

    public OrganisationDetails() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrganisationName() { return organisationName; }
    public void setOrganisationName(String organisationName) { this.organisationName = organisationName; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public String getWordTemplatePath() { return wordTemplatePath; }
    public void setWordTemplatePath(String wordTemplatePath) { this.wordTemplatePath = wordTemplatePath; }

    public String getWordTemplateFilename() { return wordTemplateFilename; }
    public void setWordTemplateFilename(String wordTemplateFilename) { this.wordTemplateFilename = wordTemplateFilename; }
}
