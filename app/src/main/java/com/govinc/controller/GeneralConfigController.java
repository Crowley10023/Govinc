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
@RequestMapping("/config/general")
public class GeneralConfigController {

    @Autowired
    private GeneralConfigService generalConfigService;

    @Autowired
    private GeneralConfigRepository generalConfigRepository;

    @GetMapping
    public String getConfigPage(Model model) {
        model.addAttribute("config", generalConfigService.getOrCreate());
        return "general-config";
    }

    @PostMapping
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
}
