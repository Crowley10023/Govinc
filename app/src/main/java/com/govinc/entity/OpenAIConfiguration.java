package com.govinc.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "openai_configuration")
public class OpenAIConfiguration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = true, length = 2000)
    private String apiKey;

    @Column(nullable = true)
    private String organization;

    @Column(nullable = true)
    private String defaultModel;

    @Column(nullable = true, length = 2000)
    private String summaryPrompt;

    public OpenAIConfiguration() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public String getSummaryPrompt() {
        return summaryPrompt;
    }

    public void setSummaryPrompt(String summaryPrompt) {
        this.summaryPrompt = summaryPrompt;
    }
}
