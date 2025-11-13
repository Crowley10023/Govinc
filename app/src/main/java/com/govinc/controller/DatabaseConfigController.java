package com.govinc.controller;

import com.govinc.entity.DatabaseConfig;
import com.govinc.service.DatabaseMigrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

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

        model.addAttribute("databaseConfig", config);
        model.addAttribute("currentVersion", config.getCurrentVersion());
        model.addAttribute("availableMigrations", availableMigrations);

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
}
