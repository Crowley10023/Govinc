package com.govinc.repository;

import com.govinc.entity.AIPromptCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AIPromptCacheRepository extends JpaRepository<AIPromptCache, Long> {
    Optional<AIPromptCache> findByPromptHashAndProviderName(String promptHash, String providerName);
    List<AIPromptCache> findAllByOrderByLastUsedDesc();
    void deleteByPromptHashAndProviderName(String promptHash, String providerName);
}
