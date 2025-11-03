package com.govinc.repository;

import com.govinc.entity.AIProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface AIProviderRepository extends JpaRepository<AIProvider, Long> {
    // Note: name field is NOT unique - we allow multiple providers of same type (e.g., multiple Ollama instances)
    // Display name is unique - enforced by database constraint
    Optional<AIProvider> findFirstByActiveTrue();
}
