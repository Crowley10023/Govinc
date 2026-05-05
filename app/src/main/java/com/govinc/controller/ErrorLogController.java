package com.govinc.controller;

import com.govinc.service.ErrorLogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

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
}