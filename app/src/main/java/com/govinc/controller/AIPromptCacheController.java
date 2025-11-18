package com.govinc.controller;

import com.govinc.entity.AIPromptCache;
import com.govinc.repository.AIPromptCacheRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/config/ai-cache")
public class AIPromptCacheController {
    private final AIPromptCacheRepository cacheRepository;

    @Autowired
    public AIPromptCacheController(AIPromptCacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
    }

    @GetMapping
    public String getCachePage(Model model) {
        List<AIPromptCache> cacheEntries = cacheRepository.findAllByOrderByLastUsedDesc();
        model.addAttribute("cacheEntries", cacheEntries);
        return "ai-cache-management";
    }

    @GetMapping(path = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> listCache() {
        try {
            List<AIPromptCache> cacheEntries = cacheRepository.findAllByOrderByLastUsedDesc();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "entries", cacheEntries
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                Map.of("success", false, "error", e.getMessage())
            );
        }
    }

    @DeleteMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> deleteEntry(@PathVariable Long id) {
        try {
            cacheRepository.deleteById(id);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Cache entry deleted successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                Map.of("success", false, "error", e.getMessage())
            );
        }
    }

    @PostMapping(path = "/clear-all", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> clearAllCache() {
        try {
            cacheRepository.deleteAll();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "All cache entries cleared successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                Map.of("success", false, "error", e.getMessage())
            );
        }
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> getEntry(@PathVariable Long id) {
        try {
            return cacheRepository.findById(id)
                .map(entry -> ResponseEntity.ok(Map.of(
                    "success", true,
                    "entry", entry
                )))
                .orElseThrow(() -> new Exception("Cache entry not found"));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(
                Map.of("success", false, "error", e.getMessage())
            );
        }
    }
}
