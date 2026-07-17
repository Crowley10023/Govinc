package com.govinc.controller;

import com.govinc.entity.GeneralConfig;
import com.govinc.repository.GeneralConfigRepository;
import com.govinc.service.GeneralConfigService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
public class GeneralConfigController {

    @Autowired
    private GeneralConfigService generalConfigService;

    @Autowired
    private GeneralConfigRepository generalConfigRepository;

    @GetMapping("/config/general")
    public String getConfigPage(Model model) {
        model.addAttribute("config", generalConfigService.getOrCreate());
        return "general-config";
    }

    @GetMapping("/config/external-access")
    public String getExternalAccessPage(Model model) {
        model.addAttribute("config", generalConfigService.getOrCreate());
        return "external-access";
    }

    @PostMapping("/config/general")
    @ResponseBody
    public ResponseEntity<?> saveConfig(@RequestBody Map<String, Object> data, HttpSession session) {
        try {
            GeneralConfig config = generalConfigService.getOrCreate();

            Object timeout = data.get("sessionTimeoutMinutes");
            if (timeout instanceof Number n) {
                int minutes = Math.max(1, Math.min(1440, n.intValue()));
                config.setSessionTimeoutMinutes(minutes);
            }

            generalConfigRepository.save(config);

            // Refresh in-memory cache so subsequent requests see new value
            generalConfigService.refresh();

            // Apply to current admin session immediately
            session.setMaxInactiveInterval(config.getSessionTimeoutMinutes() * 60);

            return ResponseEntity.ok(Map.of("success", true,
                    "sessionTimeoutMinutes", config.getSessionTimeoutMinutes()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/config/external-access")
    @ResponseBody
    public ResponseEntity<?> saveExternalAccess(@RequestBody Map<String, Object> data) {
        try {
            GeneralConfig config = generalConfigService.getOrCreate();

            Object externalAccessUrl = data.get("externalAccessUrl");
            String normalized = externalAccessUrl == null ? "" : String.valueOf(externalAccessUrl).trim();
            config.setExternalAccessUrl(normalized.isEmpty() ? null : normalized);

            generalConfigRepository.save(config);

            // Refresh in-memory cache so subsequent requests see new value
            generalConfigService.refresh();

            return ResponseEntity.ok(Map.of("success", true,
                    "externalAccessUrl", generalConfigService.getExternalAccessUrl()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
