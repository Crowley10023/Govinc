package com.govinc.assessment;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages Server-Sent Event (SSE) connections for live assessment updates.
 * Each connected client holds one SseEmitter registered here.
 * When an answer or comment is saved the controller calls broadcast()
 * to push the latest state to every subscriber of that assessment.
 */
@Service
public class AssessmentSseService {

    /** assessmentId → list of active SSE connections */
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emitters =
            new ConcurrentHashMap<>();

    /**
     * Creates and registers a new SSE emitter for the given assessment.
     *
     * @param assessmentId the assessment to subscribe to
     * @param onClose      optional callback fired when the client disconnects
     *                     (completion, timeout, or error). Runs on the servlet
     *                     container thread — keep it short and non-blocking.
     * @return the SseEmitter to return from the controller method
     */
    public SseEmitter subscribe(Long assessmentId, Runnable onClose) {
        SseEmitter emitter = new SseEmitter(0L); // 0 = never time out on the server
        CopyOnWriteArrayList<SseEmitter> list =
                emitters.computeIfAbsent(assessmentId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        Runnable cleanup = () -> {
            list.remove(emitter);
            if (onClose != null) {
                try { onClose.run(); } catch (Exception ignored) {}
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        return emitter;
    }

    /**
     * Broadcasts a named SSE event with a JSON-serialised payload to every
     * currently connected subscriber of the given assessment.
     * Dead connections are pruned automatically.
     */
    public void broadcast(Long assessmentId, String eventName, Object data) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(assessmentId);
        if (list == null || list.isEmpty()) return;
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }
        list.removeAll(dead);
    }
}
