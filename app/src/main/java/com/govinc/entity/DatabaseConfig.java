package com.govinc.entity;

import jakarta.persistence.*;

/**
 * DatabaseConfig entity to track database schema version and manage migrations.
 */
@Entity
@Table(name = "database_config")
public class DatabaseConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String versionKey = "schema_version"; // Always "schema_version"

    @Column(nullable = false)
    private String currentVersion = "0.9"; // Current schema version

    @Column(nullable = true, length = 2000)
    private String description;

    public DatabaseConfig() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getVersionKey() {
        return versionKey;
    }

    public void setVersionKey(String versionKey) {
        this.versionKey = versionKey;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
