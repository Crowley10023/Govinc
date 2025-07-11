package com.govinc.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/openai")
public class OpenAIEndpoint {
    private final OpenAIUtil openAIUtil;

    @Autowired
    public OpenAIEndpoint(OpenAIUtil openAIUtil) {
        this.openAIUtil = openAIUtil;
    }

    @PostMapping("/askAI")
    public String askAI(@RequestBody String prompt) {
        return openAIUtil.askAI(prompt);
    }
}
