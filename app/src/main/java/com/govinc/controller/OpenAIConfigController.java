package com.govinc.controller;

import com.govinc.entity.OpenAIConfiguration;
import com.govinc.entity.OpenAIConfigurationRepository;
import com.govinc.entity.LayoutConfiguration; // <-- ADD THIS
import com.govinc.entity.LayoutConfigurationRepository; // <-- ADD THIS
import com.govinc.util.OpenAIUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType; // <-- ADD THIS
import org.springframework.http.ResponseEntity; // <-- ADD THIS
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/config/openai")
public class OpenAIConfigController {
    private final OpenAIConfigurationRepository openAIConfigurationRepository;
    private final OpenAIUtil openAIUtil;
    private final LayoutConfigurationRepository layoutConfigurationRepository;

    @Autowired
    public OpenAIConfigController(OpenAIConfigurationRepository repo,
            LayoutConfigurationRepository layoutConfigRepo,
            OpenAIUtil openAIUtil) {
        this.openAIConfigurationRepository = repo;
        this.layoutConfigurationRepository = layoutConfigRepo;
        this.openAIUtil = openAIUtil;
    }

    @GetMapping
    public String getConfigPage(Model model, @RequestParam(required = false) String testResult) {
        OpenAIConfiguration config = openAIConfigurationRepository.findAll().stream().findFirst()
                .orElse(new OpenAIConfiguration());
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
        OpenAIConfiguration config = openAIConfigurationRepository.findAll().stream().findFirst()
                .orElse(new OpenAIConfiguration());
        model.addAttribute("config", config);
        model.addAttribute("testResult", response);
        return "openai-config";
    }

    @PostMapping(path = "/suggest-theme", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> suggestTheme(@RequestParam("imageFile") MultipartFile imageFile) {
        try {
            if (imageFile == null || imageFile.isEmpty()) {
                return ResponseEntity.badRequest().body("No image uploaded.");
            }
            byte[] imageBytes = imageFile.getBytes();
            String base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);

            String prompt = """
                        Given this company logo image (base64 below), suggest a modern theme with nice contrast and beautiful professional colors.
                        Respond ONLY with a single JSON object with the keys:
                          primaryColor, primaryColorDark, accentColor, backgroundColor, borderColor,
                          navViolet, textMain, shineGlare, shineHighlight, secondaryColor,
                          fontFamily, fontSizeNav, fontSizeHeadline.
                        Colors as valid hex (e.g. #RRGGBB) or rgba(...). Fonts as CSS font-family strings. Font sizes as common CSS units (e.g. 1em, 1.25em, etc).
                        Logo image (base64): %s
                    """
                    .formatted(base64Image);

            String raw = openAIUtil.askAI(prompt);

            // Extract only the JSON part if the response is wrapped in ```json ... ```
            String json = raw;
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            if (start != -1 && end != -1 && end > start) {
                json = raw.substring(start, end + 1);
            }

            // Use Jackson or org.json for safety
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, String> map = mapper.readValue(json, java.util.Map.class);

            // Persist to database (update theme fields in LayoutConfiguration)
            LayoutConfiguration config = layoutConfigurationRepository.findAll().stream()
                    .findFirst().orElse(new LayoutConfiguration());

            // Assign if present; fallback to null if missing (safe for all fields)
            config.setPrimaryColor(map.get("primaryColor"));
            config.setPrimaryColorDark(map.get("primaryColorDark"));
            config.setAccentColor(map.get("accentColor"));
            config.setBackgroundColor(map.get("backgroundColor"));
            config.setBorderColor(map.get("borderColor"));
            config.setNavViolet(map.get("navViolet"));
            config.setTextMain(map.get("textMain"));
            config.setShineGlare(map.get("shineGlare"));
            config.setShineHighlight(map.get("shineHighlight"));
            config.setSecondaryColor(map.get("secondaryColor"));
            config.setFontFamily(map.get("fontFamily"));
            config.setFontSizeNav(map.get("fontSizeNav"));
            config.setFontSizeHeadline(map.get("fontSizeHeadline"));

            layoutConfigurationRepository.save(config);

            // Respond with suggested theme JSON (so frontend can fill form immediately)
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(map);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of("error", "Error: " + e.getMessage()));
        }
    }
}
