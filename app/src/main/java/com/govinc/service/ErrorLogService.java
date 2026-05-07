package com.govinc.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Stores application exceptions in-memory for the Config > Error Log view.
 */
@Service
public class ErrorLogService {

    private static final int MAX_ENTRIES = 500;
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;
    private final Path storageFile;
    private final ConcurrentLinkedDeque<ErrorLogEntry> entries = new ConcurrentLinkedDeque<>();

    public ErrorLogService(ObjectMapper objectMapper,
                           @Value("${govinc.error-log.path:data/error-log.json}") String storagePath) {
        this.objectMapper = objectMapper;
        this.storageFile = Path.of(storagePath).toAbsolutePath().normalize();
    }

    @PostConstruct
    void loadPersistedEntries() {
        if (!Files.exists(storageFile)) {
            return;
        }
        try {
            List<ErrorLogEntry> storedEntries = objectMapper.readValue(
                    storageFile.toFile(),
                    new TypeReference<List<ErrorLogEntry>>() {});
            entries.clear();
            if (storedEntries != null) {
                for (ErrorLogEntry entry : storedEntries) {
                    entries.addLast(entry);
                }
            }
            trimToMaxEntries();
        } catch (IOException ex) {
            System.err.println("[ErrorLogService] Failed to load persisted error log: " + ex.getMessage());
        }
    }

    public synchronized void logException(Throwable ex, HttpServletRequest request) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));

        String message = ex.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = ex.getClass().getSimpleName();
        }

        String requestMethod = request != null ? request.getMethod() : "N/A";
        String requestUri = request != null ? request.getRequestURI() : "N/A";

        ErrorLogEntry entry = new ErrorLogEntry(
                TS_FORMAT.format(LocalDateTime.now()),
                ex.getClass().getName(),
                message,
                requestMethod,
                requestUri,
                sw.toString());

        entries.addFirst(entry);
        trimToMaxEntries();
        persistEntries();
    }

    public synchronized List<ErrorLogEntry> getEntries() {
        return new ArrayList<>(entries);
    }

    public synchronized void clearEntries() {
        entries.clear();
        persistEntries();
    }

    private void trimToMaxEntries() {
        while (entries.size() > MAX_ENTRIES) {
            entries.pollLast();
        }
    }

    private void persistEntries() {
        try {
            Path parent = storageFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storageFile.toFile(), new ArrayList<>(entries));
        } catch (IOException ex) {
            System.err.println("[ErrorLogService] Failed to persist error log: " + ex.getMessage());
        }
    }

    public static class ErrorLogEntry {
        private String timestamp;
        private String exceptionClass;
        private String message;
        private String requestMethod;
        private String requestUri;
        private String details;

        public ErrorLogEntry() {
        }

        public ErrorLogEntry(String timestamp,
                             String exceptionClass,
                             String message,
                             String requestMethod,
                             String requestUri,
                             String details) {
            this.timestamp = timestamp;
            this.exceptionClass = exceptionClass;
            this.message = message;
            this.requestMethod = requestMethod;
            this.requestUri = requestUri;
            this.details = details;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }

        public String getExceptionClass() {
            return exceptionClass;
        }

        public void setExceptionClass(String exceptionClass) {
            this.exceptionClass = exceptionClass;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getRequestMethod() {
            return requestMethod;
        }

        public void setRequestMethod(String requestMethod) {
            this.requestMethod = requestMethod;
        }

        public String getRequestUri() {
            return requestUri;
        }

        public void setRequestUri(String requestUri) {
            this.requestUri = requestUri;
        }

        public String getDetails() {
            return details;
        }

        public void setDetails(String details) {
            this.details = details;
        }
    }
}