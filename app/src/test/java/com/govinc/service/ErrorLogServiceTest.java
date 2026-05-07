package com.govinc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ErrorLogServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void logException_persistsEntryToDisk() throws Exception {
        Path storageFile = tempDir.resolve("logs/error-log.json");
        ErrorLogService service = new ErrorLogService(new ObjectMapper(), storageFile.toString());
        service.loadPersistedEntries();

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/failing/path");

        service.logException(new IllegalStateException("boom"), request);

        assertThat(Files.exists(storageFile)).isTrue();
        assertThat(Files.readString(storageFile)).contains("boom").contains("/failing/path");
        assertThat(service.getEntries()).hasSize(1);
    }

    @Test
    void loadPersistedEntries_restoresEntriesAcrossInstances() {
        Path storageFile = tempDir.resolve("logs/error-log.json");
        ErrorLogService writer = new ErrorLogService(new ObjectMapper(), storageFile.toString());
        writer.loadPersistedEntries();

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/restore/test");
        writer.logException(new RuntimeException("persist me"), request);

        ErrorLogService reader = new ErrorLogService(new ObjectMapper(), storageFile.toString());
        reader.loadPersistedEntries();

        assertThat(reader.getEntries()).hasSize(1);
        assertThat(reader.getEntries().get(0).getMessage()).isEqualTo("persist me");
        assertThat(reader.getEntries().get(0).getRequestMethod()).isEqualTo("POST");
        assertThat(reader.getEntries().get(0).getRequestUri()).isEqualTo("/restore/test");
    }

    @Test
    void clearEntries_removesPersistedEntries() throws Exception {
        Path storageFile = tempDir.resolve("logs/error-log.json");
        ErrorLogService service = new ErrorLogService(new ObjectMapper(), storageFile.toString());
        service.loadPersistedEntries();

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("DELETE");
        when(request.getRequestURI()).thenReturn("/clear/test");
        service.logException(new RuntimeException("clear me"), request);

        service.clearEntries();

        assertThat(service.getEntries()).isEmpty();
        assertThat(Files.readString(storageFile).trim()).startsWith("[").endsWith("]");
    }

    @Test
    void logException_trimsPersistedEntriesToMaxSize() {
        Path storageFile = tempDir.resolve("logs/error-log.json");
        ErrorLogService service = new ErrorLogService(new ObjectMapper(), storageFile.toString());
        service.loadPersistedEntries();

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/trim/test");

        for (int index = 0; index < 505; index++) {
            service.logException(new RuntimeException("err-" + index), request);
        }

        ErrorLogService reloaded = new ErrorLogService(new ObjectMapper(), storageFile.toString());
        reloaded.loadPersistedEntries();

        assertThat(reloaded.getEntries()).hasSize(500);
        assertThat(reloaded.getEntries().get(0).getMessage()).isEqualTo("err-504");
        assertThat(reloaded.getEntries().get(499).getMessage()).isEqualTo("err-5");
    }

    @Test
    void logException_usesFallbackRequestValuesWhenRequestIsNull() {
        Path storageFile = tempDir.resolve("logs/error-log.json");
        ErrorLogService service = new ErrorLogService(new ObjectMapper(), storageFile.toString());
        service.loadPersistedEntries();

        service.logException(new IOException("no request"), null);

        ErrorLogService.ErrorLogEntry entry = service.getEntries().get(0);
        assertThat(entry.getRequestMethod()).isEqualTo("N/A");
        assertThat(entry.getRequestUri()).isEqualTo("N/A");
    }
}