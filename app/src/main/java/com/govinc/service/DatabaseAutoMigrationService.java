package com.govinc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class DatabaseAutoMigrationService {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseAutoMigrationService.class);

    private final DatabaseMigrationService databaseMigrationService;

    @Value("${app.database.auto-migrate:false}")
    private boolean autoMigrate;

    @Value("${app.database.auto-migrate.target-version:}")
    private String targetVersion;

    public DatabaseAutoMigrationService(DatabaseMigrationService databaseMigrationService) {
        this.databaseMigrationService = databaseMigrationService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrateIfRequested() {
        if (!autoMigrate) {
            return;
        }

        String desiredVersion = (targetVersion != null && !targetVersion.isBlank())
                ? targetVersion.trim()
                : databaseMigrationService.getLatestKnownVersion();

        String currentVersion = databaseMigrationService.getCurrentVersion();
        if (desiredVersion.equals(currentVersion)) {
            logger.info("[DB AutoMigration] Database is already at target version {}", desiredVersion);
            return;
        }

        logger.info("[DB AutoMigration] Starting automatic migration from {} to {}", currentVersion, desiredVersion);
        try {
            databaseMigrationService.executeMigration(desiredVersion);
            logger.info("[DB AutoMigration] Automatic migration completed to version {}", desiredVersion);
        } catch (Exception e) {
            logger.error("[DB AutoMigration] Automatic migration failed: {}", e.getMessage(), e);
            throw new IllegalStateException("Automatic database migration failed", e);
        }
    }
}
