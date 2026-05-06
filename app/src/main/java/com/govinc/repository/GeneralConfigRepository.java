package com.govinc.repository;

import com.govinc.entity.GeneralConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GeneralConfigRepository extends JpaRepository<GeneralConfig, Long> {
    Optional<GeneralConfig> findByConfigKey(String configKey);
}
