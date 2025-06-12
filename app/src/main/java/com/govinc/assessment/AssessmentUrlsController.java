package com.govinc.assessment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/assessment-direct")
public class AssessmentUrlsController {
    @Autowired
    private AssessmentUrlsService assessmentUrlsService;

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
        return "assessment-urls-list";
    }
}