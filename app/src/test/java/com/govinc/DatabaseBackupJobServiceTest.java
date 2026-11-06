package com.govinc;

import com.govinc.service.DatabaseBackupJobService;
import com.govinc.service.DatabaseMigrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseBackupJobServiceTest {
    private final DatabaseMigrationService migrationService = mock(DatabaseMigrationService.class);
    private final DatabaseBackupJobService jobService = new DatabaseBackupJobService(migrationService);

    @AfterEach
    void tearDown() {
        jobService.shutdown();
    }

    @Test
    void runsInBackgroundAndRejectsOverlappingJobs() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(migrationService.createFullBackup()).thenAnswer(invocation -> {
            started.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timed out");
            return Map.of("fileName", "backup.sql");
        });

        try {
            assertThat(jobService.startBackup()).isTrue();
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(jobService.getStatus().state()).isEqualTo("RUNNING");
            assertThat(jobService.startBackup()).isFalse();
        } finally {
            release.countDown();
        }
        awaitCompletion();
        assertThat(jobService.getStatus().state()).isEqualTo("SUCCEEDED");
        assertThat(jobService.getStatus().backup()).containsEntry("fileName", "backup.sql");
    }

    @Test
    void exposesFailureWithoutPublishingBackup() throws Exception {
        when(migrationService.createFullBackup()).thenThrow(new IllegalStateException("database unavailable"));

        assertThat(jobService.startBackup()).isTrue();
        awaitCompletion();
        assertThat(jobService.getStatus().state()).isEqualTo("FAILED");
        assertThat(jobService.getStatus().backup()).isNull();
        assertThat(jobService.getStatus().error()).contains("server logs");
    }

    private void awaitCompletion() {
        org.awaitility.Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .until(() -> !"RUNNING".equals(jobService.getStatus().state()));
    }
}