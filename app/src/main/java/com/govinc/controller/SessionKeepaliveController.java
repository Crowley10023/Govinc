package com.govinc.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lightweight endpoint that the frontend calls to keep the session alive.
 * Accessing the session is sufficient to reset the server-side inactivity timer.
 */
@RestController
public class SessionKeepaliveController {

    @PostMapping("/session/keepalive")
    public ResponseEntity<?> keepalive(HttpSession session) {
        int maxInterval = session.getMaxInactiveInterval();
        return ResponseEntity.ok(Map.of(
                "timeoutSeconds", maxInterval,
                "expiresAt", System.currentTimeMillis() + ((long) maxInterval * 1000)
        ));
    }
}
