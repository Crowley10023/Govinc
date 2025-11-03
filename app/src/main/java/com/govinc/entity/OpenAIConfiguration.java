package com.govinc.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "openai_configuration")
public class OpenAIConfiguration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "active_provider_id", nullable = true)
    private AIProvider activeProvider;

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

    public AIProvider getActiveProvider() { 
        return activeProvider; 
    }
    
    public void setActiveProvider(AIProvider activeProvider) { 
        this.activeProvider = activeProvider; 
    }

    public String getSummaryPrompt() { 
        return summaryPrompt; 
    }
    
    public void setSummaryPrompt(String summaryPrompt) { 
        this.summaryPrompt = summaryPrompt; 
    }

    /**
     * Convenience method to get the provider name
     */
    public String getProviderName() {
        return activeProvider != null ? activeProvider.getName() : null;
    }

    /**
     * Convenience method to get the provider display name
     */
    public String getProviderDisplayName() {
        return activeProvider != null ? activeProvider.getDisplayName() : "No Provider Selected";
    }
}
