package com.govinc.controller;

import com.govinc.entity.DatabaseConfig;
import com.govinc.service.DatabaseMigrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Controller for database configuration and migrations.
 */
@Controller
@RequestMapping("/config/database")
public class DatabaseConfigController {
    private final DatabaseMigrationService databaseMigrationService;

    @Autowired
    public DatabaseConfigController(DatabaseMigrationService databaseMigrationService) {
        this.databaseMigrationService = databaseMigrationService;
    }

    /**
     * Display database configuration page
     */
    @GetMapping
    public String getDatabaseConfigPage(Model model) {
        DatabaseConfig config = databaseMigrationService.getDatabaseConfig();
        List<Map<String, Object>> availableMigrations = databaseMigrationService.getAvailableMigrations();
        List<Map<String, Object>> backups = databaseMigrationService.listBackups();

        model.addAttribute("databaseConfig", config);
        model.addAttribute("currentVersion", config.getCurrentVersion());
        model.addAttribute("availableMigrations", availableMigrations);
        model.addAttribute("databaseBackups", backups);

        return "database-config";
    }

    /**
     * Get database config as JSON
     */
    @GetMapping(path = "/info", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> getDatabaseInfo() {
        try {
            DatabaseConfig config = databaseMigrationService.getDatabaseConfig();
            List<Map<String, Object>> migrations = databaseMigrationService.getAvailableMigrations();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "currentVersion", config.getCurrentVersion(),
                "description", config.getDescription() != null ? config.getDescription() : "",
                "availableMigrations", migrations
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                Map.of("success", false, "error", e.getMessage())
            );
        }
    }

    /**
     * Execute a database migration
     */
    @PostMapping(path = "/migrate/{toVersion}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> executeMigration(@PathVariable String toVersion) {
        try {
            databaseMigrationService.executeMigration(toVersion);
            DatabaseConfig config = databaseMigrationService.getDatabaseConfig();
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Migration to version " + toVersion + " completed successfully",
                "currentVersion", config.getCurrentVersion()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                Map.of("success", false, "error", "Migration failed: " + e.getMessage())
            );
        }
    }

    /**
     * List all available database backups.
     */
    @GetMapping(path = "/backups", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> listBackups() {
        try {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "backups", databaseMigrationService.listBackups()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    Map.of("success", false, "error", "Failed to list backups: " + e.getMessage())
            );
        }
    }

    /**
     * Create a full database backup file.
     */
    @PostMapping(path = "/backup", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> createBackup() {
        try {
            Map<String, Object> backup = databaseMigrationService.createFullBackup();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Database backup created successfully",
                    "backup", backup
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    Map.of("success", false, "error", "Database backup failed: " + e.getMessage())
            );
        }
    }

    /**
     * Download a backup file.
     */
    @GetMapping(path = "/download/{fileName}")
    @ResponseBody
    public ResponseEntity<Resource> downloadBackup(@PathVariable String fileName) {
        try {
            Path backupFile = databaseMigrationService.getBackupFilePath(fileName);
            Resource resource = new PathResource(backupFile);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(
                    ContentDisposition.attachment().filename(fileName).build());
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            return ResponseEntity.ok().headers(headers).body(resource);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Restore database from a backup file.
     */
    @PostMapping(path = "/restore", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> restoreBackup(@RequestBody Map<String, String> body) {
        String fileName = body != null ? body.get("fileName") : null;
        try {
            Map<String, Object> result = databaseMigrationService.restoreBackup(fileName);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Database restore completed successfully",
                    "result", result
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    Map.of("success", false, "error", "Database restore failed: " + e.getMessage())
            );
        }
    }
}
