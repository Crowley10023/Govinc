package com.govinc.controller;

import com.govinc.service.ExternalApiKeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
@RequestMapping("/config/api-keys")
public class ApiKeyManagementController {

    @Autowired
    private ExternalApiKeyService externalApiKeyService;

    @GetMapping
    public String getPage(Model model) {
        model.addAttribute("apiKeys", externalApiKeyService.listAll());
        return "api-key-management";
    }

    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<?> createApiKey(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        ExternalApiKeyService.CreatedApiKey created = externalApiKeyService.createApiKey(name);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "id", created.getStored().getId(),
                "name", created.getStored().getName(),
                "prefix", created.getStored().getKeyPrefix(),
                "apiKey", created.getRawApiKey()
        ));
    }

    @PostMapping("/{id}/revoke")
    @ResponseBody
    public ResponseEntity<?> revokeApiKey(@PathVariable Long id) {
        boolean ok = externalApiKeyService.revoke(id);
        if (!ok) {
            return ResponseEntity.status(404).body(Map.of("error", "API key not found"));
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteApiKey(@PathVariable Long id) {
        boolean ok = externalApiKeyService.delete(id);
        if (!ok) {
            return ResponseEntity.status(404).body(Map.of("error", "API key not found"));
        }
        return ResponseEntity.ok(Map.of("success", true));
    }
}
