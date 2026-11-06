package com.govinc;

import com.govinc.repository.DatabaseConfigRepository;
import com.govinc.service.DatabaseMigrationService;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseBackupTest {
    @TempDir
    Path backupDirectory;

    private DatabaseMigrationService service;
    private DatabaseMetaData metadata;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseConfigRepository repository = mock(DatabaseConfigRepository.class);
        when(repository.findByVersionKey("schema_version")).thenReturn(Optional.empty());
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        metadata = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(connection.getCatalog()).thenReturn("govinc");
        service = new DatabaseMigrationService(repository, mock(JdbcTemplate.class), dataSource,
                mock(EntityManagerFactory.class));
        ReflectionTestUtils.setField(service, "databaseBackupDirectory", backupDirectory.toString());
    }

    @Test
    void failedDumpLeavesNoPublishedOrTemporaryFile() throws Exception {
        when(metadata.getTables(anyString(), any(), anyString(), any())).thenThrow(new SQLException("database unavailable"));

        assertThatThrownBy(() -> service.createFullBackup()).hasMessageContaining("database unavailable");
        try (var files = Files.list(backupDirectory)) {
            assertThat(files).isEmpty();
        }
        assertThat(service.listBackups()).isEmpty();
    }

    @Test
    void successfulDumpIsPublishedAfterCompletion() throws Exception {
        ResultSet tables = mock(ResultSet.class);
        when(metadata.getTables(anyString(), any(), anyString(), any())).thenReturn(tables);

        var backup = service.createFullBackup();

        Path file = backupDirectory.resolve((String) backup.get("fileName"));
        assertThat(Files.readString(file)).contains("SET FOREIGN_KEY_CHECKS=1;");
        assertThat(service.listBackups()).hasSize(1);
        try (var files = Files.list(backupDirectory)) {
            assertThat(files).containsExactly(file);
        }
    }

    @Test
    void runningDumpIsNotListedUntilComplete() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ResultSet tables = mock(ResultSet.class);
        when(metadata.getTables(anyString(), any(), anyString(), any())).thenAnswer(invocation -> {
            started.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new SQLException("test timed out");
            return tables;
        });
        var executor = Executors.newSingleThreadExecutor();
        try {
            var backup = executor.submit(() -> service.createFullBackup());
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(service.listBackups()).isEmpty();
            release.countDown();
            backup.get(5, TimeUnit.SECONDS);
            assertThat(service.listBackups()).hasSize(1);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}