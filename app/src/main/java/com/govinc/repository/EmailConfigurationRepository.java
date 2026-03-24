package com.govinc.repository;

import com.govinc.entity.EmailConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailConfigurationRepository extends JpaRepository<EmailConfiguration, Long> {
}
