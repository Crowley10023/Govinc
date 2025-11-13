package com.govinc.service;

import com.govinc.entity.DatabaseConfig;
import com.govinc.repository.DatabaseConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Service to initialize database configuration on application startup.
 */
@Service
public class DatabaseInitializationService {
    private final DatabaseConfigRepository databaseConfigRepository;

    @Autowired
    public DatabaseInitializationService(DatabaseConfigRepository databaseConfigRepository) {
        this.databaseConfigRepository = databaseConfigRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeDatabase() {
        try {
            // Check if database config exists
            boolean exists = databaseConfigRepository.findByVersionKey("schema_version").isPresent();
            
            if (!exists) {
                // Create default database config
                DatabaseConfig config = new DatabaseConfig();
                config.setVersionKey("schema_version");
                config.setCurrentVersion("0.9");
                config.setDescription("Initial database version");
                databaseConfigRepository.save(config);
                
                System.out.println("✓ Database configuration initialized with version 0.9");
            }
        } catch (Exception e) {
            System.err.println("⚠ Warning: Could not initialize database config: " + e.getMessage());
            // Don't fail application startup if this fails
        }
    }
}
