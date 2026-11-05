package com.govinc.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "general_config")
public class GeneralConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key", nullable = false, unique = true)
    private String configKey = "default";

    @Column(name = "session_timeout_minutes", nullable = false)
    private int sessionTimeoutMinutes = 30;

    @Column(name = "assessment_url_lifetime_days", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 30")
    private int assessmentUrlLifetimeDays = 30;

    @Column(name = "external_access_url")
    private String externalAccessUrl;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }

    public int getSessionTimeoutMinutes() { return sessionTimeoutMinutes; }
    public void setSessionTimeoutMinutes(int sessionTimeoutMinutes) { this.sessionTimeoutMinutes = sessionTimeoutMinutes; }

    public int getAssessmentUrlLifetimeDays() { return assessmentUrlLifetimeDays; }
    public void setAssessmentUrlLifetimeDays(int assessmentUrlLifetimeDays) { this.assessmentUrlLifetimeDays = assessmentUrlLifetimeDays; }

    public String getExternalAccessUrl() { return externalAccessUrl; }
    public void setExternalAccessUrl(String externalAccessUrl) { this.externalAccessUrl = externalAccessUrl; }
}
