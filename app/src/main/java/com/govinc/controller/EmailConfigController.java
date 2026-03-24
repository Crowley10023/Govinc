package com.govinc.controller;

import com.govinc.entity.EmailConfiguration;
import com.govinc.repository.EmailConfigurationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequestMapping("/config/email")
public class EmailConfigController {

    @Autowired
    private EmailConfigurationRepository emailConfigurationRepository;

    @GetMapping
    public String getConfigPage(Model model) {
        EmailConfiguration config = emailConfigurationRepository.findAll().stream()
                .findFirst()
                .orElse(new EmailConfiguration());
        model.addAttribute("config", config);
        return "email-config";
    }

    @PostMapping
    @ResponseBody
    public ResponseEntity<?> saveConfig(@RequestBody Map<String, Object> data) {
        try {
            EmailConfiguration config = emailConfigurationRepository.findAll().stream()
                    .findFirst()
                    .orElse(new EmailConfiguration());

            config.setSmtpHost(getString(data, "smtpHost"));
            config.setSmtpPort(getInt(data, "smtpPort", 587));
            config.setSmtpUsername(getString(data, "smtpUsername"));

            // Only overwrite password if a new one was provided
            String newPassword = getString(data, "smtpPassword");
            if (newPassword != null && !newPassword.isBlank()) {
                config.setSmtpPassword(newPassword);
            }

            Object tlsObj = data.get("smtpTls");
            config.setSmtpTls(tlsObj == null || Boolean.TRUE.equals(tlsObj) || "true".equalsIgnoreCase(String.valueOf(tlsObj)));

            config.setAllowedDomain(getString(data, "allowedDomain"));

            emailConfigurationRepository.save(config);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof String s ? (s.isBlank() ? null : s.trim()) : null;
    }

    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignore) {}
        }
        return defaultValue;
    }
}
