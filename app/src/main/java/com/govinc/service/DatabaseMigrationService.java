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

    private static final class MigrationDefinition {
        private final String fromVersion;
        private final String toVersion;
        private final String description;

        private MigrationDefinition(String fromVersion, String toVersion, String description) {
            this.fromVersion = fromVersion;
            this.toVersion = toVersion;
            this.description = description;
        }
    }

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

        for (MigrationDefinition definition : getMigrationDefinitions()) {
            Map<String, Object> migration = new LinkedHashMap<>();
            migration.put("fromVersion", definition.fromVersion);
            migration.put("toVersion", definition.toVersion);
            migration.put("description", definition.description);
            migration.put("available", isForwardMigrationAvailable(currentVersion, definition.toVersion));
            migrations.add(migration);
        }

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
     * Execute migration from 1.4 to 1.5
     */
    @Transactional
    public void migrateTo_1_5() throws Exception {
        System.out.println("[DB Migration] Starting migration to version 1.5");
        try {
            // Check current column type of assessments.status
            String sql = "SELECT DATA_TYPE, COLUMN_TYPE " +
                    "FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'assessments' AND COLUMN_NAME = 'status'";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

            if (rows.isEmpty()) {
                throw new Exception("Column assessments.status not found");
            }

            String dataType = rows.get(0).get("DATA_TYPE") != null
                    ? rows.get(0).get("DATA_TYPE").toString().toLowerCase(Locale.ROOT)
                    : "";

            if ("enum".equals(dataType)) {
                jdbcTemplate.execute("ALTER TABLE assessments MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'OPEN'");
                System.out.println("[DB Migration] Converted assessments.status from ENUM to VARCHAR(20)");
            } else {
                System.out.println("[DB Migration] assessments.status already compatible (" + dataType + "), no ALTER needed");
            }

            DatabaseConfig config = getDatabaseConfig();
            config.setCurrentVersion("1.5");
            config.setDescription("Converted assessments.status to VARCHAR(20) to support new REVIEW status value");
            databaseConfigRepository.save(config);
            System.out.println("[DB Migration] Successfully updated database config to version 1.5");
        } catch (Exception e) {
            System.out.println("[DB Migration] ERROR: Migration to 1.5 failed: " + e.getMessage());
            e.printStackTrace();
            throw new Exception("Migration to 1.5 failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute a specific migration
     */
    @Transactional
    public void executeMigration(String toVersion) throws Exception {
        String currentVersion = getCurrentVersion();

        if (Objects.equals(currentVersion, toVersion)) {
            return;
        }

        if (compareVersions(currentVersion, toVersion) > 0) {
            throw new Exception("Downgrade is not supported: from " + currentVersion + " to " + toVersion);
        }

        List<MigrationDefinition> chain = resolveStrictMigrationPath(currentVersion, toVersion);
        if (!chain.isEmpty()) {
            for (MigrationDefinition step : chain) {
                executeSingleMigration(step.toVersion);
            }
            return;
        }

        // Fallback: if no strict chain exists (for example 1.2 -> 1.4), execute the target patch directly.
        executeSingleMigration(toVersion);
    }

    private List<MigrationDefinition> getMigrationDefinitions() {
        return List.of(
                new MigrationDefinition("0.9", "1.0", "Fix AIProvider constraints - allow multiple providers of same type"),
                new MigrationDefinition("1.0", "1.1", "Allow multiple organization units per leader - remove unique constraint on leader_id"),
                new MigrationDefinition("1.1", "1.2", "Make maturity_answer_id column nullable in assessment_control_answer - allows saving comments without answers"),
                new MigrationDefinition("1.3", "1.4", "Normalize user.role column for new roles (convert ENUM to VARCHAR)"),
                new MigrationDefinition("1.4", "1.5", "Convert assessments.status column from ENUM to VARCHAR(20) to support new REVIEW status value")
        );
    }

    private boolean isForwardMigrationAvailable(String currentVersion, String targetVersion) {
        if (compareVersions(currentVersion, targetVersion) >= 0) {
            return false;
        }

        return !resolveStrictMigrationPath(currentVersion, targetVersion).isEmpty() || hasMigrationHandler(targetVersion);
    }

    private List<MigrationDefinition> resolveStrictMigrationPath(String fromVersion, String toVersion) {
        if (Objects.equals(fromVersion, toVersion)) {
            return Collections.emptyList();
        }

        Map<String, MigrationDefinition> byFrom = new HashMap<>();
        for (MigrationDefinition definition : getMigrationDefinitions()) {
            byFrom.put(definition.fromVersion, definition);
        }

        List<MigrationDefinition> chain = new ArrayList<>();
        String cursor = fromVersion;
        Set<String> visited = new HashSet<>();

        while (!Objects.equals(cursor, toVersion)) {
            if (!visited.add(cursor)) {
                return Collections.emptyList();
            }

            MigrationDefinition next = byFrom.get(cursor);
            if (next == null || compareVersions(next.toVersion, toVersion) > 0) {
                return Collections.emptyList();
            }

            chain.add(next);
            cursor = next.toVersion;
        }

        return chain;
    }

    private boolean hasMigrationHandler(String toVersion) {
        return "1.0".equals(toVersion) || "1.1".equals(toVersion) || "1.2".equals(toVersion) || "1.4".equals(toVersion) || "1.5".equals(toVersion);
    }

    private void executeSingleMigration(String toVersion) throws Exception {
        if ("1.0".equals(toVersion)) {
            migrateTo_1_0();
        } else if ("1.1".equals(toVersion)) {
            migrateTo_1_1();
        } else if ("1.2".equals(toVersion)) {
            migrateTo_1_2();
        } else if ("1.4".equals(toVersion)) {
            migrateTo_1_4();
        } else if ("1.5".equals(toVersion)) {
            migrateTo_1_5();
        } else {
            throw new Exception("Unknown migration target version: " + toVersion);
        }
    }

    private int compareVersions(String left, String right) {
        int[] leftParts = parseVersionParts(left);
        int[] rightParts = parseVersionParts(right);
        int maxLength = Math.max(leftParts.length, rightParts.length);

        for (int i = 0; i < maxLength; i++) {
            int leftValue = i < leftParts.length ? leftParts[i] : 0;
            int rightValue = i < rightParts.length ? rightParts[i] : 0;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return 0;
    }

    private int[] parseVersionParts(String version) {
        String[] parts = version.split("\\.");
        int[] parsed = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            parsed[i] = Integer.parseInt(parts[i]);
        }
        return parsed;
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

    /**
     * Validate the given backup file name and return its resolved Path.
     */
    public Path getBackupFilePath(String fileName) {
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
        return backupFile;
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
