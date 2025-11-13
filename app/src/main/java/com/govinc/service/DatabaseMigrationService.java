package com.govinc.service;

import com.govinc.entity.DatabaseConfig;
import com.govinc.repository.DatabaseConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service to manage database schema migrations and version control.
 */
@Service
public class DatabaseMigrationService {
    private final DatabaseConfigRepository databaseConfigRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public DatabaseMigrationService(DatabaseConfigRepository databaseConfigRepository, JdbcTemplate jdbcTemplate) {
        this.databaseConfigRepository = databaseConfigRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Get current database version
     */
    public String getCurrentVersion() {
        Optional<DatabaseConfig> config = databaseConfigRepository.findByVersionKey("schema_version");
        if (config.isPresent()) {
            return config.get().getCurrentVersion();
        }
        return "0.9"; // Default version before versioning was introduced
    }

    /**
     * Get database config
     */
    public DatabaseConfig getDatabaseConfig() {
        Optional<DatabaseConfig> config = databaseConfigRepository.findByVersionKey("schema_version");
        if (config.isPresent()) {
            return config.get();
        }
        // Create default config
        DatabaseConfig newConfig = new DatabaseConfig();
        newConfig.setVersionKey("schema_version");
        newConfig.setCurrentVersion("0.9");
        newConfig.setDescription("Initial database version");
        return databaseConfigRepository.save(newConfig);
    }

    /**
     * Get list of available migrations
     */
    public List<Map<String, Object>> getAvailableMigrations() {
        List<Map<String, Object>> migrations = new ArrayList<>();
        String currentVersion = getCurrentVersion();

        // Migration from 0.9 to 1.0
        Map<String, Object> migration_1_0 = new LinkedHashMap<>();
        migration_1_0.put("fromVersion", "0.9");
        migration_1_0.put("toVersion", "1.0");
        migration_1_0.put("description", "Fix AIProvider constraints - allow multiple providers of same type");
        migration_1_0.put("available", "0.9".equals(currentVersion));
        migrations.add(migration_1_0);

        return migrations;
    }

    /**
     * Execute migration from 0.9 to 1.0
     */
    @Transactional
    public void migrateTo_1_0() throws Exception {
        System.out.println("[DB Migration] Starting migration to version 1.0");
        try {
            // Drop the incorrect unique constraint on 'name' column
            // The constraint name is 'UK_nmrpdeu19ured81litbflx252' but we need to find the actual name
            // First, try to drop it if it exists
            try {
                System.out.println("[DB Migration] Attempting to drop UK_nmrpdeu19ured81litbflx252");
                jdbcTemplate.execute("ALTER TABLE ai_provider DROP INDEX UK_nmrpdeu19ured81litbflx252");
                System.out.println("[DB Migration] Successfully dropped UK_nmrpdeu19ured81litbflx252");
            } catch (Exception e) {
                // Constraint might not exist or have different name, continue
                System.out.println("[DB Migration] Could not drop UK_nmrpdeu19ured81litbflx252: " + e.getMessage());
            }

            // Try to find and drop any unique constraint on the 'name' column
            try {
                // Get constraint information for MariaDB
                String query = "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE " +
                        "WHERE TABLE_NAME = 'ai_provider' AND COLUMN_NAME = 'name' AND CONSTRAINT_NAME != 'PRIMARY'";
                List<Map<String, Object>> results = jdbcTemplate.queryForList(query);
                System.out.println("[DB Migration] Found " + results.size() + " constraints on 'name' column");
                for (Map<String, Object> result : results) {
                    String constraintName = (String) result.get("CONSTRAINT_NAME");
                    if (constraintName != null && !constraintName.equals("PRIMARY")) {
                        try {
                            System.out.println("[DB Migration] Dropping constraint: " + constraintName);
                            jdbcTemplate.execute("ALTER TABLE ai_provider DROP INDEX " + constraintName);
                            System.out.println("[DB Migration] Successfully dropped constraint: " + constraintName);
                        } catch (Exception e) {
                            System.out.println("[DB Migration] Could not drop constraint " + constraintName + ": " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("[DB Migration] Error while checking constraints: " + e.getMessage());
            }

            // Add unique constraint on displayName if it doesn't exist
            try {
                System.out.println("[DB Migration] Attempting to add UK_displayName constraint");
                jdbcTemplate.execute("ALTER TABLE ai_provider ADD CONSTRAINT UK_displayName UNIQUE (displayName)");
                System.out.println("[DB Migration] Successfully added UK_displayName constraint");
            } catch (Exception e) {
                // Constraint might already exist, continue
                System.out.println("[DB Migration] Could not add UK_displayName constraint (may already exist): " + e.getMessage());
            }

            // Update version
            DatabaseConfig config = getDatabaseConfig();
            config.setCurrentVersion("1.0");
            config.setDescription("Fixed AIProvider constraints - removed unique constraint on name, kept unique on displayName");
            databaseConfigRepository.save(config);
            System.out.println("[DB Migration] Successfully updated database config to version 1.0");

        } catch (Exception e) {
            System.out.println("[DB Migration] ERROR: Migration to 1.0 failed: " + e.getMessage());
            e.printStackTrace();
            throw new Exception("Migration to 1.0 failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute a specific migration
     */
    @Transactional
    public void executeMigration(String toVersion) throws Exception {
        String currentVersion = getCurrentVersion();

        if ("1.0".equals(toVersion) && "0.9".equals(currentVersion)) {
            migrateTo_1_0();
        } else {
            throw new Exception("Invalid migration: from " + currentVersion + " to " + toVersion);
        }
    }
}
