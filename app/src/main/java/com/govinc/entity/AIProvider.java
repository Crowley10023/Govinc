package com.govinc.entity;

import jakarta.persistence.*;
import java.util.HashMap;
import java.util.Map;

/**
 * AIProvider entity represents a configured AI provider.
 * Stores provider-specific settings like API keys, URLs, models, etc.
 */
@Entity
@Table(name = "ai_provider")
public class AIProvider {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name; // e.g., "openai", "ollama", "anthropic" - NOT unique to allow multiple instances

    @Column(nullable = false, unique = true)
    private String displayName; // e.g., "OpenAI API", "Ollama Local" - MUST be unique

    @Column(nullable = false)
    private boolean active = false;

    @Column(nullable = true, length = 2000)
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ai_provider_settings", joinColumns = @JoinColumn(name = "provider_id"))
    @MapKeyColumn(name = "setting_key")
    @Column(name = "setting_value", length = 2000)
    private Map<String, String> settings = new HashMap<>();

    public AIProvider() {
    }

    public AIProvider(String name, String displayName) {
        this.name = name;
        this.displayName = displayName;
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

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, String> getSettings() {
        return settings;
    }

    public void setSettings(Map<String, String> settings) {
        this.settings = settings;
    }

    public String getSetting(String key) {
        return settings.get(key);
    }

    public void setSetting(String key, String value) {
        this.settings.put(key, value);
    }
}
