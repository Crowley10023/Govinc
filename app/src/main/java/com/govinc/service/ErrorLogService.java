package com.govinc.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
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

    private final ConcurrentLinkedDeque<ErrorLogEntry> entries = new ConcurrentLinkedDeque<>();

    public void logException(Throwable ex, HttpServletRequest request) {
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
        while (entries.size() > MAX_ENTRIES) {
            entries.pollLast();
        }
    }

    public List<ErrorLogEntry> getEntries() {
        return new ArrayList<>(entries);
    }

    public static class ErrorLogEntry {
        private final String timestamp;
        private final String exceptionClass;
        private final String message;
        private final String requestMethod;
        private final String requestUri;
        private final String details;

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

        public String getExceptionClass() {
            return exceptionClass;
        }

        public String getMessage() {
            return message;
        }

        public String getRequestMethod() {
            return requestMethod;
        }

        public String getRequestUri() {
            return requestUri;
        }

        public String getDetails() {
            return details;
        }
    }
}