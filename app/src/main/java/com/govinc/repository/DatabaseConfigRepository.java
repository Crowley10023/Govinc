package com.govinc.repository;

import com.govinc.entity.DatabaseConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface DatabaseConfigRepository extends JpaRepository<DatabaseConfig, Long> {
    Optional<DatabaseConfig> findByVersionKey(String versionKey);
}
