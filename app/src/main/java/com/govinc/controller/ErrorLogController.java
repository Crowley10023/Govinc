package com.govinc.controller;

import com.govinc.service.ErrorLogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/config/error-log")
public class ErrorLogController {

    private final ErrorLogService errorLogService;

    public ErrorLogController(ErrorLogService errorLogService) {
        this.errorLogService = errorLogService;
    }

    @GetMapping
    public String showErrorLog(Model model) {
        model.addAttribute("entries", errorLogService.getEntries());
        return "error-log-config";
    }

    @PostMapping("/clear")
    public String clearErrorLog(RedirectAttributes redirectAttributes) {
        errorLogService.clearEntries();
        redirectAttributes.addFlashAttribute("clearSuccess", true);
        return "redirect:/config/error-log";
    }
}