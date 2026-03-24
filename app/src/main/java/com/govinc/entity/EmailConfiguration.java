package com.govinc.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "email_configuration")
public class EmailConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = true)
    private String smtpHost;

    @Column(nullable = true)
    private Integer smtpPort = 587;

    @Column(nullable = true)
    private String smtpUsername;

    @Column(nullable = true)
    private String smtpPassword;

    @Column(nullable = true)
    private Boolean smtpTls = true;

    /** Only emails to addresses ending with this domain are allowed (e.g. "example.com"). */
    @Column(nullable = true)
    private String allowedDomain;

    public EmailConfiguration() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSmtpHost() { return smtpHost; }
    public void setSmtpHost(String smtpHost) { this.smtpHost = smtpHost; }

    public Integer getSmtpPort() { return smtpPort; }
    public void setSmtpPort(Integer smtpPort) { this.smtpPort = smtpPort; }

    public String getSmtpUsername() { return smtpUsername; }
    public void setSmtpUsername(String smtpUsername) { this.smtpUsername = smtpUsername; }

    public String getSmtpPassword() { return smtpPassword; }
    public void setSmtpPassword(String smtpPassword) { this.smtpPassword = smtpPassword; }

    public Boolean getSmtpTls() { return smtpTls; }
    public void setSmtpTls(Boolean smtpTls) { this.smtpTls = smtpTls; }

    public String getAllowedDomain() { return allowedDomain; }
    public void setAllowedDomain(String allowedDomain) { this.allowedDomain = allowedDomain; }
}
