package com.govinc.controller;

import com.govinc.entity.AIProvider;
import com.govinc.entity.OpenAIConfiguration;
import com.govinc.repository.OpenAIConfigurationRepository;
import com.govinc.repository.AIProviderRepository;
import com.govinc.entity.LayoutConfiguration;
import com.govinc.entity.LayoutConfigurationRepository;
import com.govinc.util.OpenAIUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/config/openai")
public class OpenAIConfigController {
    private final OpenAIConfigurationRepository openAIConfigurationRepository;
    private final AIProviderRepository providerRepository;
    private final OpenAIUtil openAIUtil;
    private final LayoutConfigurationRepository layoutConfigurationRepository;

    @Autowired
    public OpenAIConfigController(
            OpenAIConfigurationRepository repo,
            AIProviderRepository providerRepository,
            LayoutConfigurationRepository layoutConfigRepo,
            OpenAIUtil openAIUtil) {
        this.openAIConfigurationRepository = repo;
        this.providerRepository = providerRepository;
        this.layoutConfigurationRepository = layoutConfigRepo;
        this.openAIUtil = openAIUtil;
    }

    @GetMapping
    public String getConfigPage(Model model, @RequestParam(required = false) String testResult) {
        // Get or create main configuration
        OpenAIConfiguration config = openAIConfigurationRepository.findAll().stream()
                .findFirst()
                .orElse(new OpenAIConfiguration());

        // Get all available providers
        List<AIProvider> allProviders = providerRepository.findAll();
        AIProvider activeProvider = config.getActiveProvider();

        // If no active provider is set, try to find one that's marked as active
        if (activeProvider == null) {
            activeProvider = providerRepository.findFirstByActiveTrue().orElse(null);
            if (activeProvider != null) {
                config.setActiveProvider(activeProvider);
                openAIConfigurationRepository.save(config);
            }
        }

        model.addAttribute("config", config);
        model.addAttribute("allProviders", allProviders);
        model.addAttribute("activeProvider", activeProvider);

        if (testResult != null) {
            model.addAttribute("testResult", testResult);
        }

        return "openai-config";
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> saveConfig(@RequestBody java.util.Map<String, Object> configData) {
        try {
            // Load existing configuration or create new one
            OpenAIConfiguration config = openAIConfigurationRepository.findAll()
                    .stream()
                    .findFirst()
                    .orElse(new OpenAIConfiguration());

            // Set active provider
            Object activeProviderIdObj = configData.get("activeProviderId");
            if (activeProviderIdObj != null) {
                Long activeProviderId = null;
                if (activeProviderIdObj instanceof Number) {
                    activeProviderId = ((Number) activeProviderIdObj).longValue();
                } else if (activeProviderIdObj instanceof String && !((String) activeProviderIdObj).isEmpty()) {
                    try {
                        activeProviderId = Long.parseLong((String) activeProviderIdObj);
                    } catch (NumberFormatException e) {
                        // Ignore, activeProviderId stays null
                    }
                }
                
                if (activeProviderId != null && activeProviderId > 0) {
                    AIProvider provider = providerRepository.findById(activeProviderId).orElse(null);
                    config.setActiveProvider(provider);
                }
            }

            // Set summary prompt
            Object summaryPromptObj = configData.get("summaryPrompt");
            if (summaryPromptObj != null) {
                config.setSummaryPrompt(summaryPromptObj.toString());
            }

            openAIConfigurationRepository.save(config);
            return ResponseEntity.ok(java.util.Map.of("success", true, "message", "Configuration saved"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                java.util.Map.of("success", false, "error", e.getMessage())
            );
        }
    }

    @PostMapping(path = "/provider/create", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> createProvider(@RequestBody AIProvider provider) {
        try {
            // Note: Display name uniqueness is enforced by database constraint
            // Provider name (type) can be duplicated to allow multiple instances of same provider type
            // (e.g., two different Ollama instances with different models)

            // Ensure only one active provider
            if (provider.isActive()) {
                List<AIProvider> allProviders = providerRepository.findAll();
                for (AIProvider p : allProviders) {
                    if (p.isActive()) {
                        p.setActive(false);
                        providerRepository.save(p);
                    }
                }
            }

            AIProvider saved = providerRepository.save(provider);
            return ResponseEntity.ok(java.util.Map.of("success", true, "provider", saved));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Handle unique constraint violation on displayName
            if (e.getMessage() != null && e.getMessage().contains("displayName")) {
                return ResponseEntity.badRequest().body(
                    java.util.Map.of("success", false, "error", "A provider with this display name already exists. Please use a different name.")
                );
            }
            return ResponseEntity.status(500).body(
                java.util.Map.of("success", false, "error", "Database error: " + e.getMessage())
            );
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                java.util.Map.of("success", false, "error", e.getMessage())
            );
        }
    }

    @PutMapping(path = "/provider/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> updateProvider(@PathVariable Long id, @RequestBody AIProvider providerData) {
        try {
            AIProvider provider = providerRepository.findById(id)
                .orElseThrow(() -> new Exception("Provider not found"));

            // Don't allow name change
            provider.setDisplayName(providerData.getDisplayName());
            provider.setDescription(providerData.getDescription());
            
            // MERGE settings instead of replacing them
            // This ensures that settings not explicitly updated are preserved
            if (providerData.getSettings() != null) {
                Map<String, String> currentSettings = provider.getSettings();
                for (java.util.Map.Entry<String, String> entry : providerData.getSettings().entrySet()) {
                    currentSettings.put(entry.getKey(), entry.getValue());
                }
                // Note: If a setting was explicitly removed from the frontend, it should be sent with an empty value or removed from the new settings map
                // The current implementation will preserve settings that aren't in the update request
            }

            // If setting to active, deactivate others
            if (providerData.isActive() && !provider.isActive()) {
                List<AIProvider> allProviders = providerRepository.findAll();
                for (AIProvider p : allProviders) {
                    if (!p.getId().equals(id) && p.isActive()) {
                        p.setActive(false);
                        providerRepository.save(p);
                    }
                }
            }

            provider.setActive(providerData.isActive());
            AIProvider saved = providerRepository.save(provider);
            return ResponseEntity.ok(java.util.Map.of("success", true, "provider", saved));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Handle unique constraint violation on displayName
            if (e.getMessage() != null && e.getMessage().contains("displayName")) {
                return ResponseEntity.badRequest().body(
                    java.util.Map.of("success", false, "error", "A provider with this display name already exists. Please use a different name.")
                );
            }
            return ResponseEntity.status(500).body(
                java.util.Map.of("success", false, "error", "Database error: " + e.getMessage())
            );
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                java.util.Map.of("success", false, "error", e.getMessage())
            );
        }
    }

    @PostMapping(path = "/provider/{id}/activate", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> activateProvider(@PathVariable Long id) {
        try {
            AIProvider provider = providerRepository.findById(id)
                .orElseThrow(() -> new Exception("Provider not found"));

            // Deactivate all other providers
            List<AIProvider> allProviders = providerRepository.findAll();
            for (AIProvider p : allProviders) {
                if (!p.getId().equals(id) && p.isActive()) {
                    p.setActive(false);
                    providerRepository.save(p);
                }
            }

            // Activate this provider
            provider.setActive(true);
            AIProvider saved = providerRepository.save(provider);

            // Update main configuration to use this provider
            OpenAIConfiguration config = openAIConfigurationRepository.findAll()
                    .stream()
                    .findFirst()
                    .orElse(new OpenAIConfiguration());
            config.setActiveProvider(saved);
            openAIConfigurationRepository.save(config);

            return ResponseEntity.ok(java.util.Map.of("success", true, "message", "Provider activated"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                java.util.Map.of("success", false, "error", e.getMessage())
            );
        }
    }

    @DeleteMapping(path = "/provider/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> deleteProvider(@PathVariable Long id) {
        try {
            AIProvider provider = providerRepository.findById(id)
                .orElseThrow(() -> new Exception("Provider not found"));

            // Don't allow deletion if it's the active provider
            if (provider.isActive()) {
                return ResponseEntity.badRequest().body(
                    java.util.Map.of("success", false, "error", "Cannot delete active provider. Please deactivate it first.")
                );
            }

            // Clear any reference from OpenAIConfiguration before deleting to avoid FK violation
            openAIConfigurationRepository.findAll().stream().findFirst().ifPresent(config -> {
                if (config.getActiveProvider() != null && config.getActiveProvider().getId().equals(id)) {
                    config.setActiveProvider(null);
                    openAIConfigurationRepository.save(config);
                }
            });

            providerRepository.deleteById(id);
            return ResponseEntity.ok(java.util.Map.of("success", true, "message", "Provider deleted"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                java.util.Map.of("success", false, "error", e.getMessage())
            );
        }
    }

    @GetMapping(path = "/provider/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> getProvider(@PathVariable Long id) {
        try {
            AIProvider provider = providerRepository.findById(id)
                .orElseThrow(() -> new Exception("Provider not found"));
            return ResponseEntity.ok(java.util.Map.of("success", true, "provider", provider));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                java.util.Map.of("success", false, "error", e.getMessage())
            );
        }
    }

    @PostMapping("/test")
    public String testOpenAI(@RequestParam String testPrompt, Model model) {
        try {
            String response = openAIUtil.askAI(testPrompt);
            model.addAttribute("testResult", response);
        } catch (Exception e) {
            model.addAttribute("testResult", "Error: " + e.getMessage());
        }

        OpenAIConfiguration config = openAIConfigurationRepository.findAll()
                .stream()
                .findFirst()
                .orElse(new OpenAIConfiguration());

        model.addAttribute("config", config);
        List<AIProvider> allProviders = providerRepository.findAll();
        model.addAttribute("allProviders", allProviders);
        model.addAttribute("activeProvider", config.getActiveProvider());

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
            Given this company logo image (base64 below), suggest a modern bright theme with nice contrast and beautiful professional colors.
            Respond ONLY with a single JSON object with the keys:
              primaryColor, primaryColorDark, accentColor, backgroundColor, borderColor,
              navViolet, textMain, shineGlare, shineHighlight, secondaryColor,
              fontFamily, fontSizeNav, fontSizeHeadline,
              successGreen, errorRed,
              modalBeige1, modalBeige2, modalBeige3, modalBeige4,
              labelGold, gray777, gray888,
              tableBg1, tableBg2, tableBg3, tableBg4, tableBg5,
              headerGradHighlight, yellowHighlight,
              tableHover1, tableHover2,
              alertBg1, alertBg2, alertColor,
              takenOverBg, dropdownBgHover,org-name-color,org-name-font-size,tool-name-color,tool-name-font-size
              fontSizeBody, fontSizeBtn, spacingBase.
            Colors as valid hex (e.g. #RRGGBB) or rgba(...). Fonts as CSS font-family strings. Font sizes as common CSS units (e.g. 1em, 1.25em, etc).
            Logo image (base64): %s
            """.formatted(base64Image);

            String raw = openAIUtil.askAI(prompt);

            String json = raw;
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            if (start != -1 && end != -1 && end > start) {
                json = raw.substring(start, end + 1);
            }

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, String> map = mapper.readValue(json, java.util.Map.class);

            LayoutConfiguration cfg = layoutConfigurationRepository.findAll().stream()
                    .findFirst().orElse(new LayoutConfiguration());

            cfg.setPrimaryColor(map.get("primaryColor"));
            cfg.setPrimaryColorDark(map.get("primaryColorDark"));
            cfg.setAccentColor(map.get("accentColor"));
            cfg.setBackgroundColor(map.get("backgroundColor"));
            cfg.setBorderColor(map.get("borderColor"));
            cfg.setNavViolet(map.get("navViolet"));
            cfg.setTextMain(map.get("textMain"));
            cfg.setShineGlare(map.get("shineGlare"));
            cfg.setShineHighlight(map.get("shineHighlight"));
            cfg.setSecondaryColor(map.get("secondaryColor"));
            cfg.setFontFamily(map.get("fontFamily"));
            cfg.setFontSizeNav(map.get("fontSizeNav"));
            cfg.setFontSizeHeadline(map.get("fontSizeHeadline"));

            cfg.setSuccessGreen(map.get("successGreen"));
            cfg.setErrorRed(map.get("errorRed"));
            cfg.setModalBeige1(map.get("modalBeige1"));
            cfg.setModalBeige2(map.get("modalBeige2"));
            cfg.setModalBeige3(map.get("modalBeige3"));
            cfg.setModalBeige4(map.get("modalBeige4"));
            cfg.setLabelGold(map.get("labelGold"));
            cfg.setGray777(map.get("gray777"));
            cfg.setGray888(map.get("gray888"));
            cfg.setTableBg1(map.get("tableBg1"));
            cfg.setTableBg2(map.get("tableBg2"));
            cfg.setTableBg3(map.get("tableBg3"));
            cfg.setTableBg4(map.get("tableBg4"));
            cfg.setTableBg5(map.get("tableBg5"));
            cfg.setHeaderGradHighlight(map.get("headerGradHighlight"));
            cfg.setYellowHighlight(map.get("yellowHighlight"));
            cfg.setTableHover1(map.get("tableHover1"));
            cfg.setTableHover2(map.get("tableHover2"));
            cfg.setAlertBg1(map.get("alertBg1"));
            cfg.setAlertBg2(map.get("alertBg2"));
            cfg.setAlertColor(map.get("alertColor"));
            cfg.setTakenOverBg(map.get("takenOverBg"));
            cfg.setDropdownBgHover(map.get("dropdownBgHover"));
            layoutConfigurationRepository.save(cfg);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(map);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of("error", "Error: " + e.getMessage()));
        }
    }

    @PostMapping(path = "/suggest-theme-noimage", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> suggestThemeNoImage() {
        try {
            String prompt = """
            Suggest a modern, professional, bright UI color theme.
            Respond ONLY with a single JSON object with the keys:
              primaryColor, primaryColorDark, accentColor, backgroundColor, borderColor,
              navViolet, textMain, shineGlare, shineHighlight, secondaryColor,
              fontFamily, fontSizeNav, fontSizeHeadline,
              successGreen, errorRed,
              modalBeige1, modalBeige2, modalBeige3, modalBeige4,
              labelGold, gray777, gray888,
              tableBg1, tableBg2, tableBg3, tableBg4, tableBg5,
              headerGradHighlight, yellowHighlight,
              tableHover1, tableHover2,
              alertBg1, alertBg2, alertColor,
              takenOverBg, dropdownBgHover,org-name-color,org-name-font-size,tool-name-color,tool-name-font-size,
              fontSizeBody, fontSizeBtn, spacingBase.
            Colors as valid hex (e.g. #RRGGBB) or rgba(...). Fonts as CSS font-family strings. Font sizes as common CSS units (e.g. 1em, 1.25em, etc).
            """;

            String raw = openAIUtil.askAI(prompt, false);

            String json = raw;
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            if (start != -1 && end != -1 && end > start) {
                json = raw.substring(start, end + 1);
            }

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, String> map = mapper.readValue(json, java.util.Map.class);
            LayoutConfiguration cfg = layoutConfigurationRepository.findAll().stream()
                    .findFirst().orElse(new LayoutConfiguration());

            cfg.setPrimaryColor(map.get("primaryColor"));
            cfg.setPrimaryColorDark(map.get("primaryColorDark"));
            cfg.setAccentColor(map.get("accentColor"));
            cfg.setBackgroundColor(map.get("backgroundColor"));
            cfg.setBorderColor(map.get("borderColor"));
            cfg.setNavViolet(map.get("navViolet"));
            cfg.setTextMain(map.get("textMain"));
            cfg.setShineGlare(map.get("shineGlare"));
            cfg.setShineHighlight(map.get("shineHighlight"));
            cfg.setSecondaryColor(map.get("secondaryColor"));
            cfg.setFontFamily(map.get("fontFamily"));
            cfg.setFontSizeNav(map.get("fontSizeNav"));
            cfg.setFontSizeHeadline(map.get("fontSizeHeadline"));
            cfg.setSuccessGreen(map.get("successGreen"));
            cfg.setErrorRed(map.get("errorRed"));
            cfg.setModalBeige1(map.get("modalBeige1"));
            cfg.setModalBeige2(map.get("modalBeige2"));
            cfg.setModalBeige3(map.get("modalBeige3"));
            cfg.setModalBeige4(map.get("modalBeige4"));
            cfg.setLabelGold(map.get("labelGold"));
            cfg.setGray777(map.get("gray777"));
            cfg.setGray888(map.get("gray888"));
            cfg.setTableBg1(map.get("tableBg1"));
            cfg.setTableBg2(map.get("tableBg2"));
            cfg.setTableBg3(map.get("tableBg3"));
            cfg.setTableBg4(map.get("tableBg4"));
            cfg.setTableBg5(map.get("tableBg5"));
            cfg.setHeaderGradHighlight(map.get("headerGradHighlight"));
            cfg.setYellowHighlight(map.get("yellowHighlight"));
            cfg.setTableHover1(map.get("tableHover1"));
            cfg.setTableHover2(map.get("tableHover2"));
            cfg.setAlertBg1(map.get("alertBg1"));
            cfg.setAlertBg2(map.get("alertBg2"));
            cfg.setAlertColor(map.get("alertColor"));
            cfg.setTakenOverBg(map.get("takenOverBg"));
            cfg.setDropdownBgHover(map.get("dropdownBgHover"));
            layoutConfigurationRepository.save(cfg);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(map);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of("error", "Error: " + e.getMessage()));
        }
    }
}
