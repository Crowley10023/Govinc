package com.govinc.service;

import com.govinc.entity.DatabaseConfig;
import com.govinc.repository.DatabaseConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Service to manage database schema migrations and version control.
 */
@Service
public class DatabaseMigrationService {
    private static final Pattern VERSION_FROM_FILENAME_PATTERN = Pattern.compile("_v([A-Za-z0-9._-]+)_\\d{8}_\\d{6}\\.sql$");

    private final DatabaseConfigRepository databaseConfigRepository;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @org.springframework.beans.factory.annotation.Value("${app.database.backup-dir:backups/database}")
    private String databaseBackupDirectory;

    @Autowired
    public DatabaseMigrationService(DatabaseConfigRepository databaseConfigRepository, JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.databaseConfigRepository = databaseConfigRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
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

        // Migration from 1.0 to 1.1
        Map<String, Object> migration_1_1 = new LinkedHashMap<>();
        migration_1_1.put("fromVersion", "1.0");
        migration_1_1.put("toVersion", "1.1");
        migration_1_1.put("description", "Allow multiple organization units per leader - remove unique constraint on leader_id");
        migration_1_1.put("available", "1.0".equals(currentVersion));
        migrations.add(migration_1_1);

        // Migration from 1.1 to 1.2
        Map<String, Object> migration_1_2 = new LinkedHashMap<>();
        migration_1_2.put("fromVersion", "1.1");
        migration_1_2.put("toVersion", "1.2");
        migration_1_2.put("description", "Make maturity_answer_id column nullable in assessment_control_answer - allows saving comments without answers");
        migration_1_2.put("available", "1.1".equals(currentVersion));
        migrations.add(migration_1_2);

        // Migration from 1.3 to 1.4
        Map<String, Object> migration_1_4 = new LinkedHashMap<>();
        migration_1_4.put("fromVersion", "1.3");
        migration_1_4.put("toVersion", "1.4");
        migration_1_4.put("description", "Normalize user.role column for new roles (convert ENUM to VARCHAR)");
        migration_1_4.put("available", "1.3".equals(currentVersion));
        migrations.add(migration_1_4);

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
     * Execute migration from 1.0 to 1.1
     */
    @Transactional
    public void migrateTo_1_1() throws Exception {
        System.out.println("[DB Migration] Starting migration to version 1.1");
        try {
            // Drop the unique constraint on leader_id column to allow one user to lead multiple org units
            try {
                System.out.println("[DB Migration] Attempting to drop UK_7vv1bxh5ptib49lxwxwgfst7m");
                jdbcTemplate.execute("ALTER TABLE org_unit DROP INDEX UK_7vv1bxh5ptib49lxwxwgfst7m");
                System.out.println("[DB Migration] Successfully dropped UK_7vv1bxh5ptib49lxwxwgfst7m");
            } catch (Exception e) {
                // Constraint might not exist or have different name, continue
                System.out.println("[DB Migration] Could not drop UK_7vv1bxh5ptib49lxwxwgfst7m: " + e.getMessage());
            }

            // Try to find and drop any unique constraint on the leader_id column
            try {
                // Get constraint information for MariaDB
                String query = "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE " +
                        "WHERE TABLE_NAME = 'org_unit' AND COLUMN_NAME = 'leader_id' AND CONSTRAINT_NAME != 'PRIMARY'";
                List<Map<String, Object>> results = jdbcTemplate.queryForList(query);
                System.out.println("[DB Migration] Found " + results.size() + " constraints on 'leader_id' column");
                for (Map<String, Object> result : results) {
                    String constraintName = (String) result.get("CONSTRAINT_NAME");
                    if (constraintName != null && !constraintName.equals("PRIMARY") && !constraintName.equals("FK_leader_id")) {
                        try {
                            System.out.println("[DB Migration] Dropping constraint: " + constraintName);
                            jdbcTemplate.execute("ALTER TABLE org_unit DROP INDEX " + constraintName);
                            System.out.println("[DB Migration] Successfully dropped constraint: " + constraintName);
                        } catch (Exception e) {
                            System.out.println("[DB Migration] Could not drop constraint " + constraintName + ": " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("[DB Migration] Error while checking constraints: " + e.getMessage());
            }

            // Update version
            DatabaseConfig config = getDatabaseConfig();
            config.setCurrentVersion("1.1");
            config.setDescription("Removed unique constraint on org_unit.leader_id to allow one user to lead multiple organization units");
            databaseConfigRepository.save(config);
            System.out.println("[DB Migration] Successfully updated database config to version 1.1");

        } catch (Exception e) {
            System.out.println("[DB Migration] ERROR: Migration to 1.1 failed: " + e.getMessage());
            e.printStackTrace();
            throw new Exception("Migration to 1.1 failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute migration from 1.1 to 1.2
     */
    @Transactional
    public void migrateTo_1_2() throws Exception {
        System.out.println("[DB Migration] Starting migration to version 1.2");
        try {
            // Make maturity_answer_id column nullable
            try {
                System.out.println("[DB Migration] Modifying maturity_answer_id to allow NULL");
                jdbcTemplate.execute("ALTER TABLE assessment_control_answer MODIFY COLUMN maturity_answer_id BIGINT NULL");
                System.out.println("[DB Migration] Successfully modified maturity_answer_id column");
            } catch (Exception e) {
                System.out.println("[DB Migration] Error modifying maturity_answer_id column: " + e.getMessage());
                throw e;
            }

            // Update version
            DatabaseConfig config = getDatabaseConfig();
            config.setCurrentVersion("1.2");
            config.setDescription("Made maturity_answer_id column nullable - allows saving comments without answers");
            databaseConfigRepository.save(config);
            System.out.println("[DB Migration] Successfully updated database config to version 1.2");

        } catch (Exception e) {
            System.out.println("[DB Migration] ERROR: Migration to 1.2 failed: " + e.getMessage());
            e.printStackTrace();
            throw new Exception("Migration to 1.2 failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute migration from 1.3 to 1.4
     */
    @Transactional
    public void migrateTo_1_4() throws Exception {
        System.out.println("[DB Migration] Starting migration to version 1.4");
        try {
            String sql = "SELECT DATA_TYPE, COLUMN_TYPE " +
                    "FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'role'";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

            if (rows.isEmpty()) {
                throw new Exception("Column user.role not found");
            }

            String dataType = rows.get(0).get("DATA_TYPE") != null
                    ? rows.get(0).get("DATA_TYPE").toString().toLowerCase(Locale.ROOT)
                    : "";
            String columnType = rows.get(0).get("COLUMN_TYPE") != null
                    ? rows.get(0).get("COLUMN_TYPE").toString().toUpperCase(Locale.ROOT)
                    : "";

            if ("enum".equals(dataType) && !columnType.contains("ASSESSOR")) {
                jdbcTemplate.execute("ALTER TABLE `user` MODIFY COLUMN role VARCHAR(64) NOT NULL");
                System.out.println("[DB Migration] Converted user.role from ENUM to VARCHAR(64)");
            } else {
                System.out.println("[DB Migration] user.role already compatible, no ALTER needed");
            }

            DatabaseConfig config = getDatabaseConfig();
            config.setCurrentVersion("1.4");
            config.setDescription("Normalized user.role column to support new role values such as ASSESSOR");
            databaseConfigRepository.save(config);
            System.out.println("[DB Migration] Successfully updated database config to version 1.4");
        } catch (Exception e) {
            System.out.println("[DB Migration] ERROR: Migration to 1.4 failed: " + e.getMessage());
            e.printStackTrace();
            throw new Exception("Migration to 1.4 failed: " + e.getMessage(), e);
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
        } else if ("1.1".equals(toVersion) && "1.0".equals(currentVersion)) {
            migrateTo_1_1();
        } else if ("1.2".equals(toVersion) && "1.1".equals(currentVersion)) {
            migrateTo_1_2();
        } else if ("1.4".equals(toVersion) && "1.3".equals(currentVersion)) {
            migrateTo_1_4();
        } else {
            throw new Exception("Invalid migration: from " + currentVersion + " to " + toVersion);
        }
    }

    /**
     * Create a full SQL dump backup of the configured MariaDB database.
     */
    public Map<String, Object> createFullBackup() throws Exception {
        Path backupDir = getBackupDirectoryPath();
        Files.createDirectories(backupDir);

        String version = getCurrentVersion();
        String safeVersion = version.replaceAll("[^A-Za-z0-9._-]", "_");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = "govinc_v" + safeVersion + "_" + timestamp + ".sql";
        Path backupFile = backupDir.resolve(fileName).normalize();

        writeFullDumpUsingJdbc(backupFile);

        return createBackupInfo(backupFile);
    }

    /**
     * List all available full dump backup files.
     */
    public List<Map<String, Object>> listBackups() {
        Path backupDir = getBackupDirectoryPath();
        if (!Files.exists(backupDir) || !Files.isDirectory(backupDir)) {
            return new ArrayList<>();
        }

        try (Stream<Path> files = Files.list(backupDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".sql"))
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .map(this::createBackupInfo)
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to list backup files: " + e.getMessage(), e);
        }
    }

    /**
     * Restore the database from a selected full dump backup file.
     */
    public Map<String, Object> restoreBackup(String fileName) throws Exception {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Backup file name is required.");
        }

        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new IllegalArgumentException("Invalid backup file name.");
        }

        Path backupDir = getBackupDirectoryPath();
        Path backupFile = backupDir.resolve(fileName).normalize();

        if (!backupFile.startsWith(backupDir)) {
            throw new IllegalArgumentException("Invalid backup file path.");
        }
        if (!Files.exists(backupFile) || !Files.isRegularFile(backupFile)) {
            throw new IllegalArgumentException("Backup file not found: " + fileName);
        }

        restoreFromDumpUsingJdbc(backupFile);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("restoredFile", fileName);
        result.put("currentVersion", getCurrentVersion());
        return result;
    }

    private Path getBackupDirectoryPath() {
        return Paths.get(databaseBackupDirectory).toAbsolutePath().normalize();
    }

    private void writeFullDumpUsingJdbc(Path backupFile) throws Exception {
        if (Files.exists(backupFile)) {
            Files.delete(backupFile);
        }

        try (Connection connection = dataSource.getConnection();
             BufferedWriter writer = Files.newBufferedWriter(backupFile, StandardCharsets.UTF_8)) {

            DatabaseMetaData metaData = connection.getMetaData();
            String databaseName = connection.getCatalog();

            writer.write("-- Govinc database backup generated at " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            writer.newLine();
            writer.write("-- Database: " + databaseName);
            writer.newLine();
            writer.write("SET FOREIGN_KEY_CHECKS=0;");
            writer.newLine();
            writer.newLine();

            List<String> baseTables = new ArrayList<>();
            List<String> views = new ArrayList<>();

            try (ResultSet rs = metaData.getTables(databaseName, null, "%", new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String tableType = rs.getString("TABLE_TYPE");
                    if (tableName == null || tableName.isBlank()) {
                        continue;
                    }
                    if ("VIEW".equalsIgnoreCase(tableType)) {
                        views.add(tableName);
                    } else {
                        baseTables.add(tableName);
                    }
                }
            }

            Collections.sort(baseTables);
            Collections.sort(views);

            for (String table : baseTables) {
                writeTableDump(connection, writer, table);
            }

            for (String view : views) {
                writeViewDump(connection, writer, view);
            }

            writer.write("SET FOREIGN_KEY_CHECKS=1;");
            writer.newLine();
        }
    }

    private void writeTableDump(Connection connection, BufferedWriter writer, String tableName) throws Exception {
        writer.write("-- Table: `" + tableName + "`");
        writer.newLine();
        writer.write("DROP TABLE IF EXISTS `" + tableName + "`;");
        writer.newLine();

        String createSql = querySingleString(connection, "SHOW CREATE TABLE `" + tableName + "`", "Create Table");
        writer.write(createSql + ";");
        writer.newLine();

        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM `" + tableName + "`")) {
            ResultSetMetaData md = rs.getMetaData();
            int colCount = md.getColumnCount();
            while (rs.next()) {
                writer.write("INSERT INTO `" + tableName + "` VALUES (");
                for (int i = 1; i <= colCount; i++) {
                    if (i > 1) {
                        writer.write(", ");
                    }
                    writer.write(toSqlLiteral(rs.getObject(i)));
                }
                writer.write(");");
                writer.newLine();
            }
        }

        writer.newLine();
    }

    private void writeViewDump(Connection connection, BufferedWriter writer, String viewName) throws Exception {
        writer.write("-- View: `" + viewName + "`");
        writer.newLine();
        writer.write("DROP VIEW IF EXISTS `" + viewName + "`;");
        writer.newLine();

        String createSql = querySingleString(connection, "SHOW CREATE VIEW `" + viewName + "`", "Create View");
        writer.write(createSql + ";");
        writer.newLine();
        writer.newLine();
    }

    private String querySingleString(Connection connection, String sql, String columnName) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            if (!rs.next()) {
                throw new IllegalStateException("No result for SQL: " + sql);
            }
            String value = rs.getString(columnName);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Missing " + columnName + " for SQL: " + sql);
            }
            return value;
        }
    }

    private void restoreFromDumpUsingJdbc(Path backupFile) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new org.springframework.core.io.FileSystemResource(backupFile));
        }
    }

    private String toSqlLiteral(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number) {
            return String.valueOf(value);
        }
        if (value instanceof Boolean b) {
            return b ? "1" : "0";
        }
        if (value instanceof byte[] bytes) {
            StringBuilder hex = new StringBuilder("X'");
            for (byte aByte : bytes) {
                hex.append(String.format("%02x", aByte));
            }
            hex.append("'");
            return hex.toString();
        }

        String text = String.valueOf(value)
                .replace("\\", "\\\\")
                .replace("'", "''")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
        return "'" + text + "'";
    }

    private Map<String, Object> createBackupInfo(Path path) {
        Map<String, Object> info = new LinkedHashMap<>();
        String fileName = path.getFileName().toString();
        info.put("fileName", fileName);
        info.put("version", extractVersionFromFileName(fileName));
        info.put("sizeBytes", getFileSize(path));
        info.put("createdAt", getCreatedAtIso(path));
        return info;
    }

    private long getFileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }

    private String getCreatedAtIso(Path path) {
        try {
            LocalDateTime created = LocalDateTime.ofInstant(
                    Files.getLastModifiedTime(path).toInstant(),
                    ZoneId.systemDefault()
            );
            return created.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (IOException e) {
            return "";
        }
    }

    private String extractVersionFromFileName(String fileName) {
        java.util.regex.Matcher matcher = VERSION_FROM_FILENAME_PATTERN.matcher(fileName);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "unknown";
    }
}
