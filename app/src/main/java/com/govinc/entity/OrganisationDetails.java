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

    @Column(columnDefinition = "TEXT")
    private String wordTemplateAnalysisJson;

    @Column(length = 64)
    private String wordTemplateChecksum;

    @Column(columnDefinition = "TEXT")
    private String wordTemplateStyleMappingJson;

    @Column(columnDefinition = "TEXT")
    private String wordTemplatePlaceholderMappingJson;

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

    public String getWordTemplateAnalysisJson() { return wordTemplateAnalysisJson; }
    public void setWordTemplateAnalysisJson(String wordTemplateAnalysisJson) { this.wordTemplateAnalysisJson = wordTemplateAnalysisJson; }

    public String getWordTemplateChecksum() { return wordTemplateChecksum; }
    public void setWordTemplateChecksum(String wordTemplateChecksum) { this.wordTemplateChecksum = wordTemplateChecksum; }

    public String getWordTemplateStyleMappingJson() { return wordTemplateStyleMappingJson; }
    public void setWordTemplateStyleMappingJson(String wordTemplateStyleMappingJson) { this.wordTemplateStyleMappingJson = wordTemplateStyleMappingJson; }

    public String getWordTemplatePlaceholderMappingJson() { return wordTemplatePlaceholderMappingJson; }
    public void setWordTemplatePlaceholderMappingJson(String wordTemplatePlaceholderMappingJson) { this.wordTemplatePlaceholderMappingJson = wordTemplatePlaceholderMappingJson; }
}
