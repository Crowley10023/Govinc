package com.govinc.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import com.govinc.authorization.AuthorizationService;

@RestController
@RequestMapping("/api/openai")
public class OpenAIEndpoint {
    private final OpenAIUtil openAIUtil;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    public OpenAIEndpoint(OpenAIUtil openAIUtil) {
        this.openAIUtil = openAIUtil;
    }

    @PostMapping("/askAI")
    public ResponseEntity<String> askAI(@RequestBody String prompt) {
        // Require authenticated + authorized users (ADMIN or Information Security Manager)
        if (authorizationService == null || !authorizationService.canAccessSecurityFramework()) {
            return ResponseEntity.status(403).body("forbidden");
        }
        String result = openAIUtil.askAI(prompt);
        return ResponseEntity.ok(result);
    }
}
