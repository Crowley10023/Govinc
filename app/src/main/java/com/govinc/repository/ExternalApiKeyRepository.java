package com.govinc.repository;

import com.govinc.entity.ExternalApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExternalApiKeyRepository extends JpaRepository<ExternalApiKey, Long> {
    Optional<ExternalApiKey> findByKeyHashAndActiveTrue(String keyHash);
    List<ExternalApiKey> findAllByOrderByCreatedAtDesc();
}
