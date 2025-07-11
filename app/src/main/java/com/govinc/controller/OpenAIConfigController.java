package com.govinc.controller;

import com.govinc.entity.OpenAIConfiguration;
import com.govinc.entity.OpenAIConfigurationRepository;
import com.govinc.util.OpenAIUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/config/openai")
public class OpenAIConfigController {
    private final OpenAIConfigurationRepository openAIConfigurationRepository;
    private final OpenAIUtil openAIUtil;

    @Autowired
    public OpenAIConfigController(OpenAIConfigurationRepository repo, OpenAIUtil openAIUtil) {
        this.openAIConfigurationRepository = repo;
        this.openAIUtil = openAIUtil;
    }

    @GetMapping
    public String getConfigPage(Model model, @RequestParam(required = false) String testResult) {
        OpenAIConfiguration config = openAIConfigurationRepository.findAll().stream().findFirst().orElse(new OpenAIConfiguration());
        model.addAttribute("config", config);
        if (testResult != null) {
            model.addAttribute("testResult", testResult);
        }
        return "openai-config";
    }

    @PostMapping
    public String saveConfig(@ModelAttribute OpenAIConfiguration config, Model model) {
        // Only one config row: update if exists, insert if not
        OpenAIConfiguration persisted = openAIConfigurationRepository.findAll().stream().findFirst().orElse(null);
        if (persisted != null) {
            persisted.setApiKey(config.getApiKey());
            persisted.setOrganization(config.getOrganization());
            persisted.setDefaultModel(config.getDefaultModel());
            persisted.setSummaryPrompt(config.getSummaryPrompt());
            openAIConfigurationRepository.save(persisted);
            model.addAttribute("config", persisted);
        } else {
            openAIConfigurationRepository.save(config);
            model.addAttribute("config", config);
        }
        model.addAttribute("saved", true);
        return "openai-config";
    }

    @PostMapping("/test")
    public String testOpenAI(@RequestParam String testPrompt, Model model) {
        String response = openAIUtil.askAI(testPrompt);
        OpenAIConfiguration config = openAIConfigurationRepository.findAll().stream().findFirst().orElse(new OpenAIConfiguration());
        model.addAttribute("config", config);
        model.addAttribute("testResult", response);
        return "openai-config";
    }
}
