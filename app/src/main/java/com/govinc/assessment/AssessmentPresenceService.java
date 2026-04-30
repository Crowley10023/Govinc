package com.govinc.assessment;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory registry of users currently active on an assessment.
 * No persistence — purely ephemeral presence tracking.
 */
@Service
public class AssessmentPresenceService {

    /** Seconds of inactivity before a user is considered gone. */
    private static final long TIMEOUT_SECONDS = 120;

    public static class PresenceEntry {
        public final String displayName;
        public volatile Instant lastSeen;

        public PresenceEntry(String displayName) {
            this.displayName = displayName;
            this.lastSeen = Instant.now();
        }

        public void touch() {
            this.lastSeen = Instant.now();
        }

        public boolean isActive() {
            return Instant.now().getEpochSecond() - lastSeen.getEpochSecond() < TIMEOUT_SECONDS;
        }
    }

    /** assessmentId → sessionKey → entry */
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, PresenceEntry>> registry =
            new ConcurrentHashMap<>();

    /**
     * Register or refresh a user's presence for an assessment.
     *
     * @param assessmentId the assessment being worked on
     * @param sessionKey   unique per-user key (e.g. HTTP session ID)
     * @param displayName  human-readable name shown to other users
     */
    public void register(Long assessmentId, String sessionKey, String displayName) {
        registry.computeIfAbsent(assessmentId, k -> new ConcurrentHashMap<>())
                .compute(sessionKey, (k, v) -> {
                    if (v == null) return new PresenceEntry(displayName);
                    v.touch();
                    return v;
                });
    }

    /**
     * Returns the display names of all active users on an assessment,
     * excluding the caller's own session so a user never sees themselves.
     */
    public List<String> getOtherUsers(Long assessmentId, String excludeSessionKey) {
        ConcurrentHashMap<String, PresenceEntry> sessions = registry.get(assessmentId);
        if (sessions == null) return Collections.emptyList();
        return sessions.entrySet().stream()
                .filter(e -> !e.getKey().equals(excludeSessionKey) && e.getValue().isActive())
                .map(e -> e.getValue().displayName)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Returns the display names of ALL active users on an assessment (including the caller).
     * Used for SSE presence broadcasts where the client filters out itself.
     */
    public List<String> getAllUsers(Long assessmentId) {
        ConcurrentHashMap<String, PresenceEntry> sessions = registry.get(assessmentId);
        if (sessions == null) return Collections.emptyList();
        return sessions.values().stream()
                .filter(PresenceEntry::isActive)
                .map(e -> e.displayName)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Immediately removes a user's presence entry (called on SSE disconnect).
     */
    public void remove(Long assessmentId, String sessionKey) {
        ConcurrentHashMap<String, PresenceEntry> sessions = registry.get(assessmentId);
        if (sessions != null) sessions.remove(sessionKey);
    }

    /** Periodic cleanup of stale entries — runs every 60 seconds. */
    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        registry.forEach((assessmentId, sessions) ->
                sessions.entrySet().removeIf(e -> !e.getValue().isActive()));
        registry.entrySet().removeIf(e -> e.getValue().isEmpty());
    }
}
