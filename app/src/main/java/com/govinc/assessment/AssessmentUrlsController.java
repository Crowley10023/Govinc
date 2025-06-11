package com.govinc.assessment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/assessment-direct")
public class AssessmentUrlsController {
    @Autowired
    private AssessmentUrlsService assessmentUrlsService;

    @PostMapping("/{id}/urls/create")
    public Map<String, String> createOrReplaceUrl(@PathVariable Long id) {
        AssessmentUrls url = assessmentUrlsService.createOrReplaceUrl(id);
        String fullUrl = "/assessment-direct/" + url.getUrl();
        return Map.of("directUrl", fullUrl);
    }

    // New endpoints for prolong and delete
    @PostMapping("/urls/{id}/prolong")
    public ResponseEntity<?> prolongLifetime(@PathVariable Long id) {
        assessmentUrlsService.prolongLifetime(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/urls/{id}")
    public ResponseEntity<?> deleteUrl(@PathVariable Long id) {
        assessmentUrlsService.deleteUrl(id);
        return ResponseEntity.ok().build();
    }

    // --- Add POST handler for deletion (for HTML Form Compatibility) ---
    @PostMapping("/urls/{id}/delete")
    public ResponseEntity<?> deleteUrlPost(@PathVariable Long id) {
        assessmentUrlsService.deleteUrl(id);
        return ResponseEntity.ok().build();
    }
}
