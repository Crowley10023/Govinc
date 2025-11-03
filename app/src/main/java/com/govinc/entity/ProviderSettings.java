package com.govinc.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "provider_settings")
public class ProviderSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String provider;

    @Column(nullable = false, length = 100)
    private String settingKey;

    @Column(nullable = false, length = 2000)
    private String settingValue;

    public ProviderSettings() {
    }

    public ProviderSettings(String provider, String settingKey, String settingValue) {
        this.provider = provider;
        this.settingKey = settingKey;
        this.settingValue = settingValue;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getSettingKey() {
        return settingKey;
    }

    public void setSettingKey(String settingKey) {
        this.settingKey = settingKey;
    }

    public String getSettingValue() {
        return settingValue;
    }

    public void setSettingValue(String settingValue) {
        this.settingValue = settingValue;
    }
}
