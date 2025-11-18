package com.govinc.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_prompt_cache", indexes = {
    @Index(name = "idx_prompt_hash", columnList = "promptHash"),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
public class AIPromptCache {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2000)
    private String prompt;

    @Column(nullable = false, columnDefinition = "CHAR(64)")
    private String promptHash;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String response;

    @Column(nullable = false)
    private String providerName;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime lastUsed;

    @Column(nullable = false)
    private Integer hitCount = 1;

    public AIPromptCache() {
    }

    public AIPromptCache(String prompt, String promptHash, String response, String providerName) {
        this.prompt = prompt;
        this.promptHash = promptHash;
        this.response = response;
        this.providerName = providerName;
        this.createdAt = LocalDateTime.now();
        this.lastUsed = LocalDateTime.now();
        this.hitCount = 1;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getPromptHash() {
        return promptHash;
    }

    public void setPromptHash(String promptHash) {
        this.promptHash = promptHash;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastUsed() {
        return lastUsed;
    }

    public void setLastUsed(LocalDateTime lastUsed) {
        this.lastUsed = lastUsed;
    }

    public Integer getHitCount() {
        return hitCount;
    }

    public void setHitCount(Integer hitCount) {
        this.hitCount = hitCount;
    }

    public void recordHit() {
        this.hitCount = (this.hitCount != null ? this.hitCount : 0) + 1;
        this.lastUsed = LocalDateTime.now();
    }
}
