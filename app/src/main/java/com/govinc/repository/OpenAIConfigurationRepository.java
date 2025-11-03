package com.govinc.repository;

import com.govinc.entity.OpenAIConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


public interface OpenAIConfigurationRepository extends JpaRepository<OpenAIConfiguration, Long> {
}