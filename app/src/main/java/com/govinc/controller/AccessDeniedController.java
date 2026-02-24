package com.govinc.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Controller for handling access denied scenarios.
 * Displays a user-friendly "Not Authorized" page when users don't have
 * the required permissions to access a resource.
 */
@Controller
public class AccessDeniedController {

    @GetMapping("/not-authorized")
    public String notAuthorized(HttpServletRequest request, Model model) {
        String message = (String) request.getAttribute("message");
        if (message == null) {
            message = "You do not have the necessary permissions to access this page or perform this action.";
        }
        
        model.addAttribute("message", message);
        
        return "not-authorized";
    }
}
