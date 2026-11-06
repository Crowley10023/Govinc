package com.govinc.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class DatabaseBackupJobService {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseBackupJobService.class);

    public record BackupStatus(String state, Map<String, Object> backup, String error) { }

    private final DatabaseMigrationService databaseMigrationService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile BackupStatus status = new BackupStatus("IDLE", null, null);

    public DatabaseBackupJobService(DatabaseMigrationService databaseMigrationService) {
        this.databaseMigrationService = databaseMigrationService;
    }

    public synchronized boolean startBackup() {
        if ("RUNNING".equals(status.state())) {
            return false;
        }
        status = new BackupStatus("RUNNING", null, null);
        try {
            executor.execute(this::runBackup);
        } catch (RuntimeException exception) {
            logger.error("Could not start database backup", exception);
            status = new BackupStatus("FAILED", null, "Could not start the database backup.");
            throw exception;
        }
        return true;
    }

    public BackupStatus getStatus() {
        return status;
    }

    private void runBackup() {
        try {
            Map<String, Object> backup = databaseMigrationService.createFullBackup();
            status = new BackupStatus("SUCCEEDED", backup, null);
        } catch (Exception exception) {
            logger.error("Database backup failed", exception);
            status = new BackupStatus("FAILED", null, "Database backup failed. Check the server logs for details.");
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}