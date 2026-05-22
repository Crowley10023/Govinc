package com.govinc.controller;

import com.govinc.entity.DatabaseConfig;
import com.govinc.service.BackupExtractService;
import com.govinc.service.BackupExtractService.BackupSnapshot;
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
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Controller for database configuration and migrations.
 */
@Controller
@RequestMapping("/config/database")
public class DatabaseConfigController {
    private final DatabaseMigrationService databaseMigrationService;
    private final BackupExtractService backupExtractService;

    @Autowired
    public DatabaseConfigController(DatabaseMigrationService databaseMigrationService,
                                    BackupExtractService backupExtractService) {
        this.databaseMigrationService = databaseMigrationService;
        this.backupExtractService = backupExtractService;
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
     * Import a backup .sql file uploaded from the browser and restore the database.
     */
    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> importBackup(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "tables", required = false) List<String> tables) {
        try {
            Set<String> selectedEntities = (tables != null && !tables.isEmpty())
                    ? new LinkedHashSet<>(tables) : null;
            Map<String, Object> result = databaseMigrationService.importBackup(file, selectedEntities);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Backup imported and restored successfully",
                    "result", result
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "error", e.getMessage())
            );
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    Map.of("success", false, "error", "Import failed: " + e.getMessage())
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

    /**
     * List assessments contained in a backup file (without restoring it).
     */
    @GetMapping(path = "/backups/{fileName}/assessments", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> listAssessmentsInBackup(@PathVariable String fileName) {
        try {
            Path backupFile = databaseMigrationService.getBackupFilePath(fileName);
            BackupSnapshot snap = backupExtractService.parseBackup(backupFile);
            List<Map<String, Object>> assessments = backupExtractService.listAssessments(snap);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "fileName", fileName,
                    "assessments", assessments
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "error", e.getMessage())
            );
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    Map.of("success", false, "error", "Failed to parse backup: " + e.getMessage())
            );
        }
    }

    /**
     * Extract a single assessment from a backup file as an Excel report.
     */
    @GetMapping(path = "/backups/{fileName}/assessments/{assessmentId}/excel")
    @ResponseBody
    public ResponseEntity<?> extractAssessmentExcel(@PathVariable String fileName,
                                                    @PathVariable Long assessmentId) {
        try {
            Path backupFile = databaseMigrationService.getBackupFilePath(fileName);
            BackupSnapshot snap = backupExtractService.parseBackup(backupFile);
            byte[] excel = backupExtractService.extractAssessmentExcel(snap, assessmentId);
            String safeBackup = fileName.replaceAll("\\.sql$", "");
            String downloadName = "assessment_" + assessmentId + "_from_" + safeBackup + ".xlsx";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(ContentDisposition.attachment().filename(downloadName).build());
            headers.setContentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            return ResponseEntity.ok().headers(headers).body(excel);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "error", e.getMessage())
            );
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    Map.of("success", false, "error", "Failed to extract assessment: " + e.getMessage())
            );
        }
    }
}
