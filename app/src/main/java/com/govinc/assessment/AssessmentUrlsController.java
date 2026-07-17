package com.govinc.assessment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.govinc.service.GeneralConfigService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/assessment-direct")
public class AssessmentUrlsController {
    @Autowired
    private AssessmentUrlsService assessmentUrlsService;

    @Autowired
    private GeneralConfigService generalConfigService;

    @PostMapping("/{id}/urls/create")
    @ResponseBody
    public Map<String, String> createOrReplaceUrl(@PathVariable Long id) {
        AssessmentUrls url = assessmentUrlsService.createOrReplaceUrl(id);
        String fullUrl = "/assessment-direct/" + url.getUrl();
        return Map.of("directUrl", fullUrl);
    }

    // Prolong button - return redirect to list
    @PostMapping("/urls/{id}/prolong")
    public String prolongLifetime(@PathVariable Long id) {
        assessmentUrlsService.prolongLifetime(id);
        return "redirect:/assessment-direct/urls";
    }

    // Delete button for HTML form - return redirect
    @PostMapping("/urls/{id}/delete")
    public String deleteUrlPost(@PathVariable Long id) {
        assessmentUrlsService.deleteUrl(id);
        return "redirect:/assessment-direct/urls";
    }

    // For API deletion (Json/REST)
    @DeleteMapping("/urls/{id}")
    @ResponseBody
    public Map<String, String> deleteUrl(@PathVariable Long id) {
        assessmentUrlsService.deleteUrl(id);
        return Map.of("status", "deleted");
    }

    // List page
    @GetMapping("/urls")
    public String listUrls(Model model) {
        List<AssessmentUrls> urls = assessmentUrlsService.findAll();
        model.addAttribute("urls", urls);
        model.addAttribute("externalUrlById", urls.stream()
            .collect(java.util.stream.Collectors.toMap(AssessmentUrls::getId,
                url -> generalConfigService.buildConfiguredExternalAssessmentDirectUrl(url.getUrl()))));
        return "assessment-urls-list";
    }

    // Manual expiration check — deletes all URLs that have passed their expiration date
    @PostMapping("/urls/check-expiration")
    public String checkExpiration() {
        assessmentUrlsService.cleanupExpiredUrls();
        return "redirect:/assessment-direct/urls";
    }

    // Generate a random password
    @PostMapping("/urls/{id}/generate-password")
    @ResponseBody
    public Map<String, String> generatePassword(@PathVariable Long id) {
        String password = assessmentUrlsService.generatePassword();
        assessmentUrlsService.setPassword(id, password);
        return Map.of("password", password, "status", "success");
    }

    // Set a custom password
    @PostMapping("/urls/{id}/set-password")
    @ResponseBody
    public Map<String, String> setPassword(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String password = body.get("password");
        if (password != null && !password.trim().isEmpty()) {
            assessmentUrlsService.setPassword(id, password.trim());
            return Map.of("status", "success", "message", "Password set successfully");
        } else {
            assessmentUrlsService.setPassword(id, null);
            return Map.of("status", "success", "message", "Password removed");
        }
    }

    // Clear password for a URL
    @PostMapping("/urls/{id}/clear-password")
    @ResponseBody
    public Map<String, String> clearPassword(@PathVariable Long id) {
        assessmentUrlsService.setPassword(id, null);
        return Map.of("status", "success", "message", "Password cleared");
    }

    // Retrieve existing password for a URL
    @GetMapping("/urls/{id}/get-password")
    @ResponseBody
    public ResponseEntity<Map<String, String>> getPassword(@PathVariable Long id) {
        AssessmentUrls url = assessmentUrlsService.findById(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noStore().mustRevalidate().getHeaderValue());
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");

        if (url != null && url.getPassword() != null) {
            return ResponseEntity.ok().headers(headers).body(Map.of("password", url.getPassword()));
        }
        return ResponseEntity.ok().headers(headers).body(Map.of("password", ""));
    }
}